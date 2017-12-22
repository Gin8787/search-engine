
/*
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.3.1.
 */
import java.io.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.Map.Entry;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

/**
 * This software illustrates the architecture for the portion of a search engine
 * that evaluates queries. It is a guide for class homework assignments, so it
 * emphasizes simplicity over efficiency. It implements an unranked Boolean
 * retrieval model, however it is easily extended to other retrieval models. For
 * more information, see the ReadMe.txt file.
 */
public class QryEval {

	// --------------- Constants and variables ---------------------

	private static final String USAGE = "Usage:  java QryEval paramFile\n\n";

	private static final String[] TEXT_FIELDS = { "body", "title", "url", "inlink" };

	// --------------- Methods ---------------------------------------

	/**
	 * @param args
	 *            The only argument is the parameter file name.
	 * @throws Exception
	 *             Error accessing the Lucene index.
	 */
	public static void main(String[] args) throws Exception {

		// This is a timer that you may find useful. It is used here to
		// time how long the entire program takes, but you can move it
		// around to time specific parts of your code.

		Timer timer = new Timer();
		timer.start();

		// Check that a parameter file is included, and that the required
		// parameters are present. Just store the parameters. They get
		// processed later during initialization of different system
		// components.

		if (args.length < 1) {
			throw new IllegalArgumentException(USAGE);
		}

		Map<String, String> parameters = readParameterFile(args[0]);

		// Open the index and initialize the retrieval model.

		Idx.open(parameters.get("indexPath"));

		String algorithm = parameters.get("retrievalAlgorithm");
		if ((algorithm != null) && (algorithm.equals("letor"))) {
			processLToR(parameters);
		} else {
			RetrievalModel model = null;
			if(! parameters.containsKey("diversity:initialRankingFile")) {
				model = initializeRetrievalModel(parameters);
			}
			// Perform experiments.

			processQueryFile(parameters.get("queryFilePath"), parameters.get("trecEvalOutputPath"),
					parameters.get("trecEvalOutputLength"), model, parameters);

		}
		// Clean up.

		timer.stop();
		System.out.println("Time:  " + timer);
	}

	/**
	 * Allocate the retrieval model and initialize it using parameters from the
	 * parameter file.
	 * 
	 * @return The initialized retrieval model
	 * @throws IOException
	 *             Error accessing the Lucene index.
	 */
	private static RetrievalModel initializeRetrievalModel(Map<String, String> parameters) throws IOException {

		RetrievalModel model = null;
		String modelString = parameters.get("retrievalAlgorithm").toLowerCase();

		if (modelString.equals("unrankedboolean")) {
			model = new RetrievalModelUnrankedBoolean();
		} else if (modelString.equals("rankedboolean")) {
			model = new RetrievalModelRankedBoolean();
		} else if (modelString.equals("bm25")) {
			String k_1 = parameters.get("BM25:k_1");
			String b = parameters.get("BM25:b");
			String k_3 = parameters.get("BM25:k_3");
			model = new RetrievalModelBM25(k_1, b, k_3);
		} else if (modelString.equals("indri")) {
			String mu = parameters.get("Indri:mu");
			String lambda = parameters.get("Indri:lambda");
			model = new RetrievalModelIndri(mu, lambda);
		} else {
			throw new IllegalArgumentException("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
		}

		return model;
	}

	/**
	 * Print a message indicating the amount of memory used. The caller can indicate
	 * whether garbage collection should be performed, which slows the program but
	 * reduces memory usage.
	 * 
	 * @param gc
	 *            If true, run the garbage collector before reporting.
	 */
	public static void printMemoryUsage(boolean gc) {

		Runtime runtime = Runtime.getRuntime();

		if (gc)
			runtime.gc();

		System.out
				.println("Memory used:  " + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
	}

	/**
	 * Process one query.
	 * 
	 * @param qString
	 *            A string that contains a query.
	 * @param model
	 *            The retrieval model determines how matching and scoring is done.
	 * @return Search results
	 * @throws IOException
	 *             Error accessing the index
	 */
	static ScoreList processQuery(String qString, RetrievalModel model) throws IOException {

		String defaultOp = model.defaultQrySopName();
		qString = defaultOp + "(" + qString + ")";
		Qry q = QryParser.getQuery(qString);

		// Show the query that is evaluated

		System.out.println("    --> " + q);

		if (q != null) {

			ScoreList r = new ScoreList();

			if (q.args.size() > 0) { // Ignore empty queries

				q.initialize(model);

				while (q.docIteratorHasMatch(model)) {
					int docid = q.docIteratorGetMatch();
					double score = ((QrySop) q).getScore(model);
					r.add(docid, score);
					q.docIteratorAdvancePast(docid);
				}
			}

			return r;
		} else
			return null;
	}

	/**
	 * Process the query file.
	 * 
	 * @param queryFilePath
	 * @param model
	 * @throws Exception
	 */
	static void processQueryFile(String queryFilePath, String outputFilePath, String resultLen, RetrievalModel model,
			Map<String, String> parameters) throws Exception {

		BufferedReader input = null;

		try {
			String qLine = null;

			input = new BufferedReader(new FileReader(queryFilePath));

			// Each pass of the loop processes one query.

			// Added by Zijie for outputing to file
			File file = new File(outputFilePath);
			BufferedWriter writer = new BufferedWriter(new FileWriter(file));

			Map<String, ScoreList> qryId_ScoreList = new HashMap<>();
			Boolean needScale = false;
			if (parameters.containsKey("fbInitialRankingFile")) {
				qryId_ScoreList = readFbInitialRankingFile(parameters.get("fbInitialRankingFile"));
			}
			if (parameters.containsKey("diversity:initialRankingFile")) {
				qryId_ScoreList = readDiversityInitialRankingFile(parameters.get("diversity:initialRankingFile"), needScale);
				needScale = true;
				System.out.println("needScale is " + needScale);
			}

			File fileNewQry;
			BufferedWriter writerNewQry = null;
			if (parameters.containsKey("fbExpansionQueryFile")) {
				fileNewQry = new File(parameters.get("fbExpansionQueryFile"));
				writerNewQry = new BufferedWriter(new FileWriter(fileNewQry));
			}

			while ((qLine = input.readLine()) != null) {
				int d = qLine.indexOf(':');

				if (d < 0) {
					throw new IllegalArgumentException("Syntax error:  Missing ':' in query line.");
				}

				printMemoryUsage(false);

				String qid = qLine.substring(0, d);
				String query = qLine.substring(d + 1);

				System.out.println("Query " + qLine);

				ScoreList r = null;
				ScoreList finalRanking = null;
				if(parameters.containsKey("diversity") && parameters.get("diversity").equals("true")) {
					if(!(parameters.containsKey("diversity:maxInputRankingsLength") &&
							parameters.containsKey("diversity:maxResultRankingLength") &&
							parameters.containsKey("diversity:algorithm") &&
//							parameters.containsKey("diversity:intentsFile") &&
							parameters.containsKey("diversity:lambda"))) {
						throw new IllegalArgumentException("Required parameters were missing from the parameter file.");
					}
					
					ScoreList rOriginal = null;
					List<ScoreList> rIntents = null;
					int maxInputRankingLength = Integer.parseInt(parameters.get("diversity:maxInputRankingsLength"));
					
					if(parameters.containsKey("diversity:initialRankingFile")) {
						rOriginal = qryId_ScoreList.get(qid);
						rIntents = getInitialIntentRanking(parameters, qryId_ScoreList,  qid);
						
						int minLength = Math.min(rOriginal.size(), maxInputRankingLength);
						rOriginal.truncate(minLength);
						for(int i = 0; i < rIntents.size(); i++) {
							rIntents.get(i).truncate(minLength);
						}
					} else {
						rOriginal = processQuery(query, model);
						rOriginal.sort();	
						int minLength = Math.min(rOriginal.size(), maxInputRankingLength);
						rOriginal.truncate(minLength);
						rIntents = processQueryIntentFile(parameters, model, qid, minLength);
						if((parameters.get("retrievalAlgorithm").equals("BM25"))) {
							needScale = true;
						}
					}				
					
					int rOrigianlSize = rOriginal.size();
					// Truncate intent query ranking
//					for(int i = 0; i < rIntents.size(); i++) {
//						rIntents.get(i).truncate(rOrigianlSize);
//					}
					
						
					// Scale
					if(needScale) {
						double maxScore = maxScoreSum(rOriginal, rIntents);
						scaleScoreList(rOriginal, maxScore);
						
						for(int i = 0; i < rIntents.size(); i++) {
							scaleScoreList(rIntents.get(i), maxScore);
						}
					
					}
					
//					System.out.println("After scaling, the score is");
//					for(int i = 0; i < rOriginal.size(); i++) {
//						int docid = rOriginal.getDocid(i);
//						String externalId = Idx.getExternalDocid(docid);
//						
//						double scoreOri = rOriginal.getDocidScore(i);
//						System.out.print(externalId + " " + scoreOri + " ");
//						for(int j = 0; j< rIntents.size(); j++) {
//							double scoreIntent = rIntents.get(j).getScore(docid);
//							System.out.print(scoreIntent + " ");
//						}
//						System.out.println();
//					}
					
					// Using xQuad to rerank
					if(parameters.get("diversity:algorithm").equals("xQuAD")) {
						finalRanking = xQuAD(rOriginal, rIntents, parameters);
					}
					
					if(parameters.get("diversity:algorithm").equals("PM2")) {
						finalRanking = PM2(rOriginal, rIntents, parameters);
					}
					finalRanking.sort();
				} 
				else if((parameters.containsKey("fb") && parameters.get("fb").equals("true"))){
					if (!(parameters.containsKey("fbDocs") && parameters.containsKey("fbTerms")
							&& parameters.containsKey("fbMu") && parameters.containsKey("fbOrigWeight")
							&& parameters.containsKey("fbExpansionQueryFile"))) {
						throw new IllegalArgumentException("Required parameters were missing from the parameter file.");
					}

					ScoreList rOriginal;

					if (parameters.containsKey("fbInitialRankingFile")) {

						rOriginal = qryId_ScoreList.get(qid);
					} else {
						rOriginal = processQuery(query, model);
						rOriginal.sort();
					}

					String qryLearned = expandQuery(rOriginal, parameters);

					System.out.print("The learned query is ");
					System.out.println(qryLearned);

					// TODO: construct new query
					String fbOrigWeight = parameters.get("fbOrigWeight");
					Double fbLearnedWeight = ((Double) 1.0 - Double.parseDouble(fbOrigWeight));
					String newQry = "#wand(" + fbOrigWeight + " #and(" + query + ") " + fbLearnedWeight.toString() + " "
							+ qryLearned + ")";
					System.out.print("The new query is ");
					System.out.println(newQry);
					r = processQuery(newQry, model);
					writerNewQry.write(qid + ": " + qryLearned + "\n");
				} else {
					r = processQuery(query, model);
				}
				if(parameters.containsKey("diversity") && parameters.get("diversity").equals("true")) {
					printResultsDiversity(qid, finalRanking, writer, parameters.get("diversity:maxResultRankingLength"));
				} 
				else if (r != null) {
					r.sort();
					printResults(qid, r, writer, resultLen);
					System.out.println();
				}
			}

			writer.flush();
			writer.close();
			if (parameters.containsKey("fbExpansionQueryFile")) {
				writerNewQry.flush();
				writerNewQry.close();
			}

		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			input.close();
		}
	}
	
	/**
	 * Using PM-2 algorithm to re-rank the scorelist
	 * @throws IOException 
	 * */
	private static ScoreList PM2(ScoreList rOriginal, List<ScoreList> rIntents, Map<String, String> parameters) throws IOException {
		int maxResultRankingLength = Integer.parseInt(parameters.get("diversity:maxResultRankingLength"));
		double lambda = Double.parseDouble(parameters.get("diversity:lambda"));
		
		int intentNum = rIntents.size();
		double v_i = ((double)rOriginal.size()) / ((double)intentNum);
		
		double qt[] = new double[intentNum];
		double s[] = new double[intentNum];
		
		for(int i = 0; i < intentNum; i++) {
			qt[i] = 0;
			s[i] = 0;
		}
		
		ScoreList finalRank = new ScoreList();
		
		boolean remainZero = false;
		
		while((finalRank.size() < maxResultRankingLength) && (rOriginal != null) && (remainZero == false)) {
			// Update qt[] array
			double max_qt_i = Double.MIN_VALUE;
			int i_star = 0;
			for(int i = 0; i < intentNum; i++) {
				qt[i] = v_i / (2 * s[i] + 1.0);
				if(max_qt_i < qt[i]) {
					max_qt_i = qt[i];
					i_star = i;
				}
			}
			
//			double maxScore = Double.MIN_VALUE;
			double maxScore = Double.NEGATIVE_INFINITY;
			int maxDocId = Integer.MIN_VALUE;
			int maxIndex = Integer.MIN_VALUE;
//			System.out.print("PM2 before for maxDocId is");
//			System.out.println(maxDocId);
			int originalLength = rOriginal.size();
//			System.out.print("originalLength is");
//			System.out.println(originalLength);
//			System.out.print("MaxScore before for is");
//			System.out.println(maxScore);
			for(int j = 0; j < originalLength; j++) {
//				if(originalLength == 64) {
//					System.out.println("Debugging...");
//				}
				ScoreList currentIntent = rIntents.get(i_star);
				int docId = rOriginal.getDocid(j);
				double firstTerm = lambda * qt[i_star] * currentIntent.getScore(docId);
				double secondTermTmp = 0.0;
				String externalDocId = Idx.getExternalDocid(docId);
//				if(externalDocId.equals("clueweb09-enwp03-28-01117")) {
//					System.out.println("debugging...");
//				}
				for(int i = 0; i < intentNum; i++) {
					if(i != i_star) {
						ScoreList iIntent = rIntents.get(i);
						secondTermTmp = secondTermTmp + (qt[i] * iIntent.getScore(docId));
//						secondTermTmp = secondTermTmp + (qt[i] * iIntent.getScore(i));
					}
				}
				double secondTerm = secondTermTmp * (1 - lambda);
				double newScore = firstTerm + secondTerm;
//				System.out.print("PM2 newScore is");
//				System.out.println(newScore);
//				System.out.print("MaxScore is");
//				System.out.println(maxScore);
//				System.out.print("PM2 before updating maxDocId is");
//				System.out.println(maxDocId);
				if(maxScore < newScore) {
					maxScore = newScore;
					maxDocId = docId;
					maxIndex = j;
//					System.out.print("PM2 updating for maxDocId is");
//					System.out.println(maxDocId);
				}
			}
//			System.out.print("PM2 adding for maxDocId is");
//			System.out.println(maxDocId);
			finalRank.add(maxDocId, maxScore);
			rOriginal.remove(maxIndex);
			
			//Update s_i
			// Calculate the denominator
			double denominator = 0.0;
			for(int i = 0; i < intentNum; i++) {
				denominator = denominator + rIntents.get(i).getScore(maxDocId);
			}
			// Update each s_i
//			System.out.print("PM2 maxDocId is");
//			System.out.println(maxDocId);
			if(denominator != 0) {
				for(int i = 0; i < intentNum; i++) {
					s[i] = s[i] + rIntents.get(i).getScore(maxDocId) / denominator;
				}
			} else {
				remainZero = true;
			}
		}
		
		if(remainZero) {
			int index = 0;
			while((finalRank.size() < maxResultRankingLength) && (rOriginal != null)) {
				int docId = rOriginal.getDocid(index);
				double score = 0.0 - 0.0001 * (double)index;
				finalRank.add(docId, score);
				index++;
			}
			
		}
		return finalRank;
	}
	
	/**
	 * Using xQuAD algorithm to re-rank the scorelist
	 * @throws IOException 
	 * */
	private static ScoreList xQuAD(ScoreList rOriginal, List<ScoreList> rIntents, Map<String, String> parameters) throws IOException {
		int maxResultRankingLength = Integer.parseInt(parameters.get("diversity:maxResultRankingLength"));
		double lambda = Double.parseDouble(parameters.get("diversity:lambda"));
				
		int intentNum = rIntents.size();
		double intentWeight = 1 / (double)intentNum;
		
		ScoreList finalRank = new ScoreList();
		
		while((finalRank.size() < maxResultRankingLength) && (rOriginal != null)) {
			double maxScore = Double.NEGATIVE_INFINITY;
			int maxDocId = Integer.MIN_VALUE;
			int maxIndex = Integer.MIN_VALUE;
			int originalLength = rOriginal.size();
			
			
			// One round to find the best document in set R
			
			for(int i = 0; i < originalLength; i++) {
				double relevance = (1.0 - lambda) * rOriginal.getDocidScore(i);
				int docId = rOriginal.getDocid(i);
				String externalId = Idx.getExternalDocid(docId);
				
//				if(externalId.equals(("clueweb09-enwp01-22-00778"))) {
//					System.out.println("start debuggin in XQuaD");
//					System.out.println("intentWegith is " + intentWeight);
//					System.out.println("relevance is " + relevance);
//				}
				
				// Calculate diversity
				double diversityTmp = 0.0;
				for(int j = 0; j < intentNum; j++) {
					ScoreList rIntent = rIntents.get(j);
//					double intenDiversityScore = 1.0;
					
					double Pq_i_d = rIntent.getScore(docId);
					
					if(externalId.equals(("clueweb09-enwp01-22-00778"))) {
						System.out.println("Pq_i_d is" + Pq_i_d);
					}
					
					double diversityCoverage = 1.0;
					for(int finalIndex = 0; finalIndex < finalRank.size(); finalIndex++) {
//						if(externalId.equals(("clueweb09-enwp01-22-00778"))) {
//							System.out.println("diversityCoverage is" + diversityCoverage);
//						}
						int docIdCoverage = finalRank.getDocid(finalIndex);
						diversityCoverage = diversityCoverage * (1.0 - rIntent.getScore(docIdCoverage));
						
					}
					
					double diversityEachIntent = intentWeight * Pq_i_d * diversityCoverage;
					
//					if(externalId.equals(("clueweb09-enwp01-22-00778"))) {
//						System.out.println("diversityEachIntent is" + diversityEachIntent);
//					}
					
					diversityTmp = diversityTmp + diversityEachIntent;
//					
//					if(externalId.equals(("clueweb09-enwp01-22-00778"))) {
//						System.out.println("diversityTmp is" + diversityTmp);
//					}
				}
				
				double diversity = lambda * diversityTmp;
				double newScore = relevance + diversity;
				if(externalId.equals(("clueweb09-enwp01-22-00778"))) {
					System.out.println("newScore is" + newScore);
				}
				
				if(newScore > maxScore) {
					maxScore = newScore;
					maxDocId = docId;
					maxIndex = i;
				}
			}
			
			finalRank.add(maxDocId, maxScore);
			rOriginal.remove(maxIndex);
		}
		
		return finalRank;
		
	}
	
	private static List<ScoreList> getInitialIntentRanking(Map<String, String> parameters, Map<String, ScoreList> qryId_ScoreList, String qid) throws IOException {
		BufferedReader input = null;
		try {
			String qLine = null;
			String queryIntentFile = parameters.get("diversity:intentsFile");
			input = new BufferedReader(new FileReader(queryIntentFile));
			List<ScoreList> intentScoreListAll = new ArrayList<>();
			while((qLine = input.readLine()) != null) {
				int d = qLine.indexOf(':');
				
				if(d < 0) {
					throw new IllegalArgumentException("Syntax error: Missing ':' in query line. ");
				}
				printMemoryUsage(false);
				
				String qidIntent = qLine.substring(0, d);
				if(! qidIntent.contains(qid)) {
					continue;
				}
				System.out.println("Query Intent " + qLine);
				
				ScoreList rIntentAll = qryId_ScoreList.get(qidIntent);
				
				
				intentScoreListAll.add(rIntentAll);
			}
			return intentScoreListAll;
			
		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			input.close();
		}
		
		System.out.println("getInitialIntentRanking Read Intent return null");
		return null;
	}
	
	/**
	 * readDiversityInitialRankingFile 
	 * @throws Exception 
	 * */
	private static Map<String, ScoreList> readDiversityInitialRankingFile(String filePath, Boolean needScale) throws Exception {
		Map<String, ScoreList> qryId_ScoreList = new HashMap<>();
		BufferedReader reader = null;
		reader = new BufferedReader(new FileReader(filePath));
		String rLine;
		while((rLine = reader.readLine()) != null) {
			String[] data = rLine.split(" ");
			String qidStr = data[0];
			String externalId = data[2];
			String scoreStr = data[4];

			Double score = Double.parseDouble(scoreStr);
			if(score > 1.0) {
				needScale = true;
			}
			int internalId = Idx.getInternalDocid(externalId);

			if (qryId_ScoreList.containsKey(qidStr)) {
				qryId_ScoreList.get(qidStr).add(internalId, score);
			} else {
				ScoreList rTmp = new ScoreList();
				rTmp.add(internalId, score);
				qryId_ScoreList.put(qidStr, rTmp);
			}
		}
		reader.close();
		return qryId_ScoreList;
	}
	
	/**
	 * This function is used to scale the score list
	 * */
	private static void scaleScoreList(ScoreList r, double maxValue) {
		for(int i = 0; i < r.size(); i++) {
			double newScore = r.getDocidScore(i) / maxValue;
			r.setDocidScore(i, newScore);
		}
	}
	
	/**
	 * Calculate the maximum value sum of document scores
	 * Return the maximum value sum of document scores
	 * */
	private static double maxScoreSum(ScoreList rOriginal, List<ScoreList> rIntent) {
		System.out.print("In function ScoreSum");
		double maxValue = Double.MIN_VALUE;
		//List<Integer> sizeOfScoreList = new ArrayList<>();
		List<Double> scoreSumEachIntent = new ArrayList<>();
		double cummulateScoreOrignial = 0;
		// sizeOfScoreList.add(rOriginal.size());
		// scoreSumEachIntent.add(0.0);
		for(int i = 0; i < rIntent.size(); i++) {
			//sizeOfScoreList.add(rIntent.get(i).size());
			scoreSumEachIntent.add(0.0);
		}
		int originalSize = rOriginal.size();
		for(int docIndex = 0; docIndex < originalSize; docIndex++) {
			cummulateScoreOrignial = cummulateScoreOrignial + rOriginal.getDocidScore(docIndex);
			
			// For each intent calculate the accumulated score
			for(int intentIndex = 0; intentIndex < rIntent.size(); intentIndex++) {
				ScoreList intent = rIntent.get(intentIndex);
				int intentSize = intent.size();
				if((intentSize < originalSize) && (docIndex >= intentSize )) {
					continue;
				}
				int docIdIntent = intent.getDocid(docIndex);
				
				// check if the document is in the orignial scorelist
				
				int exist = rOriginal.checkDocExist(docIdIntent, originalSize);
				if(exist >= 0) {
					double cummulateScoreIntent = scoreSumEachIntent.get(intentIndex) + intent.getDocidScore(docIndex);
					scoreSumEachIntent.set(intentIndex, cummulateScoreIntent);
				}
			}
		}
//		System.out.println("Output accumulate scores");
		maxValue = cummulateScoreOrignial;
//		System.out.println(cummulateScoreOrignial);
		for(int i = 0; i < scoreSumEachIntent.size(); i++) {
			System.out.println(scoreSumEachIntent.get(i));
			if(maxValue < scoreSumEachIntent.get(i)) {
				maxValue = scoreSumEachIntent.get(i);
			}
		}
//		System.out.println("The max value is");
//		System.out.println(maxValue);
//		System.out.println("This is the end of function ScoreSum");
		return maxValue;
	}
	
	
	/**
	 * Read query intent file and return scoreList of different query intent
	 * @throws IOException 
	 * */
	private static List<ScoreList> processQueryIntentFile(Map<String, String> parameters, RetrievalModel model, String qid, int minLength) throws IOException {
		BufferedReader input = null;
		
		List<ScoreList> intentScoreListAll = null;
		try {
			String qLine = null;
			String queryIntentFile = parameters.get("diversity:intentsFile");
			input = new BufferedReader(new FileReader(queryIntentFile));
			intentScoreListAll = new ArrayList<>();
			while((qLine = input.readLine()) != null) {
				ScoreList rIntentAll = null;
				int d = qLine.indexOf(':');
				
				if(d < 0) {
					throw new IllegalArgumentException("Syntax error: Missing ':' in query line. ");
				}
				printMemoryUsage(false);
				
				String qidIntent = qLine.substring(0, d);
				if(! qidIntent.contains(qid)) {
					continue;
				}
//				if(! qidIntent.equals(qid)) {
//					continue;
//				}
				String query = qLine.substring(d + 1);
				System.out.println("Query Intent " + qLine);
				
				
//				int maxInputRankingLength = Integer.parseInt(parameters.get("diversity:maxInputRankingsLength"));
				rIntentAll = processQuery(query, model);
				rIntentAll.sort();
				
				rIntentAll.truncate(minLength);
//				int minLength = Math.min(rIntentAll.size(), maxInputRankingLength);
//				rIntentAll.truncate(minLength);
//				for(int i = 0; i < minLength; i++) {					
//					rIntent.add(rIntentAll.getDocid(i), rIntentAll.getDocidScore(i));
//				}
				
				intentScoreListAll.add(rIntentAll);
			}
			//return intentScoreListAll;
			
		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			input.close();
		}
		
		if(intentScoreListAll == null) {
			System.out.println("processQueryIntentFile Read Intent return null");
		}
		return intentScoreListAll;
	}
	
	private static Map<String, ScoreList> readFbInitialRankingFile(String filePath) throws Exception {
		Map<String, ScoreList> qryId_ScoreList = new HashMap<>();
		BufferedReader fbInitialRankingFileReader = null;
		fbInitialRankingFileReader = new BufferedReader(new FileReader(filePath));
		String rLine;
		while ((rLine = fbInitialRankingFileReader.readLine()) != null) {
			String[] data = rLine.split(" ");
			String qidStr = data[0];
			String externalId = data[2];
			String scoreStr = data[4];

			Double score = Double.parseDouble(scoreStr);
			int internalId = Idx.getInternalDocid(externalId);

			if (qryId_ScoreList.containsKey(qidStr)) {
				qryId_ScoreList.get(qidStr).add(internalId, score);
			} else {
				ScoreList rTmp = new ScoreList();
				rTmp.add(internalId, score);
				qryId_ScoreList.put(qidStr, rTmp);
			}
		}
		fbInitialRankingFileReader.close();
		return qryId_ScoreList;
	}

	private static Map<String, Double> getTermListInTop(ScoreList r, Map<String, String> parameters)
			throws IOException {
		Map<String, Double> termList = new HashMap<>();
		Integer fbDocs = Integer.parseInt(parameters.get("fbDocs"));
		int topNDoc = Math.min(fbDocs, r.size());
		for (int i = 0; i < topNDoc; i++) {
			int docId = r.getDocid(i);
			TermVector terms = new TermVector(docId, "body");
			for (int j = 1; j < terms.stemsLength(); j++) {
				String term = terms.stemString(j);
				if (term.contains(".") || term.contains(",")) {
					continue;
				}
				double ctf = (double) terms.totalStemFreq(j);
				if (!termList.containsKey(term)) {
					termList.put(term, ctf);
				}
			}
		}
		return termList;
	}

	private static String expandQuery(ScoreList r, Map<String, String> parameters) throws IOException {
		// System.out.println("In expand query");
		Integer fbDocs = Integer.parseInt(parameters.get("fbDocs"));
		Integer fbTerms = Integer.parseInt(parameters.get("fbTerms"));
		Double fbMu = Double.parseDouble(parameters.get("fbMu"));
		// System.out.print("fbDocs is ");
		// System.out.println(fbDocs);
		// System.out.print("fbMu is ");
		// System.out.println(fbMu);
		Map<String, Double> termScore = new HashMap<>();
		int topNDoc = Math.min(fbDocs, r.size());
		double corpusLen = (double) Idx.getSumOfFieldLengths("body");
		Map<String, Double> termList = getTermListInTop(r, parameters);
		for (int i = 0; i < topNDoc; i++) {
			int docId = r.getDocid(i);
			// System.out.print("docid");
			// System.out.println(docId);
			TermVector terms = new TermVector(docId, "body");
			double docScore = r.getDocidScore(i);
			double docLen = Idx.getFieldLength("body", docId);
			// System.out.print("The stemsLengths is ");
			// System.out.println(terms.stemsLength());
			for (String term : termList.keySet()) {
				// String term = terms.stemString(j);

				if (term == null) {
					System.out.println("The term is null");
				}
				if (term.contains(".") || term.contains(",")) {
					continue;
				}
				int index = terms.indexOfStem(term);
				double tf;
				if (index == -1) {
					tf = 0.0;
				} else {
					tf = (double) terms.stemFreq(index);
				}
				docLen = Idx.getFieldLength("body", docId);
				corpusLen = (double) Idx.getSumOfFieldLengths("body");

				double ctf = termList.get(term);
				double pMLE = ctf / corpusLen;
				double idf = Math.log(1.0 / pMLE);
				double p_td = (tf + fbMu * pMLE) / (docLen + fbMu);
				double p_Id = docScore;
				Double p_tI = p_Id * p_td * idf;

				if (termScore.containsKey(term)) {
					double scoreTmp = termScore.get(term) + p_tI;
					termScore.put(term, scoreTmp);
				} else {
					termScore.put(term, p_tI);
				}

			}
		}
		System.out.println("In expand query end of for");

		PriorityQueue<Entry<String, Double>> pQueueTermScore = new PriorityQueue<Map.Entry<String, Double>>(
				termScore.size(), new Comparator<Map.Entry<String, Double>>() {
					@Override
					public int compare(Map.Entry<String, Double> term1, Map.Entry<String, Double> term2) {
						return term2.getValue().compareTo(term1.getValue());
					}
				});

		pQueueTermScore.addAll(termScore.entrySet());
		System.out.println("In expand query end of priority queue");

		String learnedQry = "#wand(";
		for (int k = 0; k < fbTerms; k++) {
			String score = String.format("%.4f", pQueueTermScore.peek().getValue());
			String termStr = pQueueTermScore.peek().getKey();
			learnedQry = learnedQry + score + " " + termStr + " ";
			pQueueTermScore.poll();
		}
		learnedQry = learnedQry + ")";
		System.out.println("Expand query finished");
		return learnedQry;
	}

	static void processLToR(Map<String, String> parameters) throws Exception {
		RetrievalModel BM25Model = initializeLToRRetrievalModel(parameters, "BM25");
		RetrievalModel IndriModel = initializeLToRRetrievalModel(parameters, "Indri");

		generateTrainingData(parameters, BM25Model, IndriModel);
		trainingSVM(parameters);
		
		List<String> qid = new ArrayList<String>();
		List<List<String>> tempResult = null;
		tempResult = generateTestingData(parameters, qid, BM25Model, IndriModel);
		testingSVM(parameters);
		
		printResultsSVM(qid, tempResult, parameters);
	}
	
	private static List<List<String>> generateTestingData(Map<String, String> parameters, List<String> qidAll, RetrievalModel BM25Model,
			RetrievalModel IndriModel) throws Exception {
		System.out.println("In function generateTestingData");
		String TestingQueryFilePath = parameters.get("queryFilePath");
		String TestingFeatureFilePath = parameters.get("letor:testingFeatureVectorsFile");
		
		String qLine = null;
		
		BufferedReader input = new BufferedReader(new FileReader(TestingQueryFilePath));
		BufferedWriter featureWriter = new BufferedWriter(new FileWriter(TestingFeatureFilePath));
		
		List<List<String>> tempResult = new ArrayList<>();
		
		while((qLine = input.readLine()) != null) {
			int d = qLine.indexOf(':');

			if (d < 0) {
				throw new IllegalArgumentException("Syntax error:  Missing ':' in query line.");
			}

			printMemoryUsage(false);

			String qid = qLine.substring(0, d);
			String query = qLine.substring(d + 1);
			
			qidAll.add(qid);
			
			System.out.println("Query " + qLine);
			
			List<String> queryStems = new ArrayList<>();
			queryStems = getQueryTerms(query);
			
			int[] featureOn = new int[19];
			Arrays.fill(featureOn, 1);
			if (parameters.containsKey("letor:featureDisable")) {
				String disableFeatureStr = parameters.get("letor:featureDisable");
				disableFeature(disableFeatureStr, featureOn);
				// String[] disableNum = disableFeature.split(",", 2);
			}

			List<int[]> featureExistEveryDoc = new ArrayList<int[]>();

			double[] featureMin = new double[19];
			double[] featureMax = new double[19];

			Arrays.fill(featureMin, Double.MAX_VALUE);
			Arrays.fill(featureMax, Double.MIN_VALUE);

			List<double[]> features = new ArrayList<>();
			List<String> externalId = new ArrayList<>();
			List<String> target = new ArrayList<>();
			
			ScoreList r = null;
			r = processQuery(query, BM25Model);
			r.sort();
			
			int rankDocNum = Math.min(100, r.size());
			System.out.println("Before for");
			List<String> tempResultQry = new ArrayList<>();
			for (int i = 0; i < rankDocNum; i++) {
				double[] feature = new double[19];
				int[] featureExistDoc = new int[19];
				Arrays.fill(feature, Double.MAX_VALUE);
				String externalIdDoc = Idx.getExternalDocid(r.getDocid(i));
				feature = calculateFeatures(featureOn, externalIdDoc, queryStems, BM25Model, IndriModel, featureExistDoc);
				
				if (feature == null) {
					continue;
				}
				target.add("0");
				externalId.add(externalIdDoc);
				
				updateFeatureExtreme(feature, featureMin, featureMax, featureExistDoc, 18);
				features.add(feature);
				featureExistEveryDoc.add(featureExistDoc);
				
				tempResultQry.add(externalIdDoc);
			}
			System.out.println("for end");
			normalizeFeature(featureMin, featureMax, features, featureExistEveryDoc, 18);
			printFeatures(featureWriter, features, featureOn, target, qid, externalId, 18);
			tempResult.add(tempResultQry);
			
		}
		input.close();
		featureWriter.flush();
		featureWriter.close();
		return tempResult;
	}
	
	private static void testingSVM(Map<String, String> parameters) throws Exception {
		System.out.println("In testingSVM");
		String execPath = parameters.get("letor:svmRankClassifyPath");
		String testingFeatureOutputFile = parameters.get("letor:testingFeatureVectorsFile");
		String modelFile = parameters.get("letor:svmRankModelFile");
		String testingDocScoreFile = parameters.get("letor:testingDocumentScores");
		
		Process cmdProc = Runtime.getRuntime().exec(
				new String[] {execPath, testingFeatureOutputFile, modelFile, testingDocScoreFile});
		
		BufferedReader stdoutreader = new BufferedReader(new InputStreamReader(cmdProc.getInputStream()));
		String line;
		while((line = stdoutreader.readLine()) != null) {
			System.out.println(line);
		}
		
		BufferedReader stderrReader = new BufferedReader(new InputStreamReader(cmdProc.getErrorStream()));
		while((line = stderrReader.readLine()) != null) {
			System.out.println(line);
		}
		
		int retValue = cmdProc.waitFor();
		if (retValue !=  0) {
			throw new Exception("SVM Rank crashed.");
		}
	}
	
	private static void trainingSVM(Map<String, String> parameters) throws Exception {
		String execPath = parameters.get("letor:svmRankLearnPath");
		String qrelsFeatureOutputFile = parameters.get("letor:trainingFeatureVectorsFile");
		String letor_c = parameters.get("letor:svmRankParamC");
		String modelOutputFile = parameters.get("letor:svmRankModelFile");
		Process cmdProc = Runtime.getRuntime().exec(
				new String[] {execPath, "-c", String.valueOf(letor_c), qrelsFeatureOutputFile,
						modelOutputFile});
		
		BufferedReader stdoutreader = new BufferedReader(new InputStreamReader(cmdProc.getInputStream()));
		String line;
		while((line = stdoutreader.readLine()) != null) {
			System.out.println(line);
		}
		
		BufferedReader stderrReader = new BufferedReader(new InputStreamReader(cmdProc.getErrorStream()));
		while((line = stderrReader.readLine()) != null) {
			System.out.println(line);
		}
		
		int retValue = cmdProc.waitFor();
		if (retValue !=  0) {
			throw new Exception("SVM Rank crashed.");
		}
	}

	private static void generateTrainingData(Map<String, String> parameters, RetrievalModel BM25Model,
			RetrievalModel IndriModel) throws Exception {
		String TrainingQueryFilePath = parameters.get("letor:trainingQueryFile");
		String TrainingQrelsFilePath = parameters.get("letor:trainingQrelsFile");
		String TrainingFeatureFilePath = parameters.get("letor:trainingFeatureVectorsFile");

		String qLine = null;

		BufferedReader input = new BufferedReader(new FileReader(TrainingQueryFilePath));
		BufferedWriter featureWriter = new BufferedWriter(new FileWriter(TrainingFeatureFilePath));

		List<String> qrelStrs = readQrelFile(TrainingQrelsFilePath);
		int qrelIndex = 0;
		int qrelLength = qrelStrs.size();
		while ((qLine = input.readLine()) != null) {
			int d = qLine.indexOf(':');

			if (d < 0) {
				throw new IllegalArgumentException("Syntax error:  Missing ':' in query line.");
			}

			printMemoryUsage(false);

			String qid = qLine.substring(0, d);
			String query = qLine.substring(d + 1);

			System.out.println("Query " + qLine);

			List<String> queryStems = new ArrayList<>();
			queryStems = getQueryTerms(query);

			// ScoreList initialRankIndri = processQuery(query, IndriModel);
			// ScoreList intiialRankBM25 = processQuery(query, BM25Model);

			int[] featureOn = new int[19];
			Arrays.fill(featureOn, 1);
			if (parameters.containsKey("letor:featureDisable")) {
				String disableFeatureStr = parameters.get("letor:featureDisable");
				disableFeature(disableFeatureStr, featureOn);
				// String[] disableNum = disableFeature.split(",", 2);
			}

			List<int[]> featureExistEveryDoc = new ArrayList<int[]>();

			double[] featureMin = new double[19];
			double[] featureMax = new double[19];

			Arrays.fill(featureMin, Double.MAX_VALUE);
			Arrays.fill(featureMax, Double.MIN_VALUE);

			List<double[]> features = new ArrayList<>();
			List<String> externalId = new ArrayList<>();
			List<String> target = new ArrayList<>();

			while (qrelIndex < qrelLength) {
				String qrelLine = qrelStrs.get(qrelIndex);
				String[] qrelSubstrings = qrelLine.split("[ \\t\\n\\r]+", 4);
				if (!qid.equals(qrelSubstrings[0])) {
					break;
				}
//				System.out.print("External Id is ");
//				System.out.println(qrelSubstrings[2]);
				double[] feature = new double[19];
				int[] featureExistDoc = new int[19];
				Arrays.fill(feature, Double.MAX_VALUE);
				feature = calculateFeatures(featureOn, qrelSubstrings[2], queryStems, BM25Model, IndriModel,
						featureExistDoc);
				if (feature == null) {
					qrelIndex++;
					continue;
				}
				target.add(qrelSubstrings[3]);
				externalId.add(qrelSubstrings[2]);

				updateFeatureExtreme(feature, featureMin, featureMax, featureExistDoc, 18);
				features.add(feature);
				featureExistEveryDoc.add(featureExistDoc);
				qrelIndex++;
			}

			normalizeFeature(featureMin, featureMax, features, featureExistEveryDoc, 18);
			printFeatures(featureWriter, features, featureOn, target, qid, externalId, 18);

		}
		input.close();
		featureWriter.flush();
		featureWriter.close();
	}

	private static void normalizeFeature(double[] featureMin, double[] featureMax, List<double[]> features,
			List<int[]> featureExistEveryDoc, int featureNum) {
		System.out.println("In function normalizeFeature");
		int docsNum = features.size();
		for (int i = 0; i < docsNum; i++) {
			double[] feature = features.get(i);
			int[] featureExistDoc = featureExistEveryDoc.get(i);
			for (int j = 1; j < (featureNum + 1); j++) {
				if (((featureMax[j] - featureMin[j]) == 0) || (featureExistDoc[j] == 0)) {
					feature[j] = 0;
				} else {
					feature[j] = (feature[j] - featureMin[j]) / (featureMax[j] - featureMin[j]);
				}
			}
			features.set(i, feature);
		}
	}

	private static void updateFeatureExtreme(double[] feature, double[] featureMin, double[] featureMax,
			int[] featureExistDoc, int featureNum) {
		int arrayLen = featureNum + 1;
		for (int i = 0; i < arrayLen; i++) {
			if (featureExistDoc[i] > 0) {
				if (feature[i] < featureMin[i]) {
					featureMin[i] = feature[i];
				}
				if (feature[i] > featureMax[i]) {
					featureMax[i] = feature[i];
				}
			}
		}
	}

	private static double[] calculateFeatures(int[] featureOn, String externalId, List<String> queryStems,
			RetrievalModel BM25Model, RetrievalModel IndriModel, int[] featureExistDoc) throws Exception {
		double[] feature = new double[19];
		Arrays.fill(feature, -1.0);
		try {
			int docId = Idx.getInternalDocid(externalId);
			Arrays.fill(featureExistDoc, 1);
			if (featureOn[1] == 1) {
				// need to get spam score feature
				if (Idx.getAttribute("spamScore", docId) != null) {
					int spamScore = Integer.parseInt(Idx.getAttribute("spamScore", docId));
					feature[1] = (double) spamScore;
				} else {
					featureExistDoc[1] = 0;
				}
			}
			if (featureOn[2] == 1) {
				// need to get Url depth feature
				if (Idx.getAttribute("rawUrl", docId) != null) {
					String rawUrl = Idx.getAttribute("rawUrl", docId);
					int slashNum = countNumOfChar(rawUrl, '/');
					feature[2] = (double) slashNum;
				} else {
					featureExistDoc[2] = 0;
				}
			}
			if (featureOn[3] == 1) {
				// need to get Wikipedia feature
				if (Idx.getAttribute("rawUrl", docId) != null) {
					String rawUrl = Idx.getAttribute("rawUrl", docId);
					if (rawUrl.contains("wikipedia.org")) {
						feature[3] = 1.0;
					} else {
						feature[3] = 0.0;
					}
				} else {
					featureExistDoc[3] = 0;
				}
			}
			if (featureOn[4] == 1) {
				// need to get Page Rank feature
				if (Idx.getAttribute("PageRank", docId) != null) {
					//double prScore = Double.parseDouble(Idx.getAttribute("PageRank", docId));
					float prScore =  Float.parseFloat (Idx.getAttribute ("PageRank", docId));
					feature[4] = prScore;
				} else {
					featureExistDoc[4] = 0;
				}
			}
			if (featureOn[5] == 1) {
				// need to get BM25 body feature
				feature[5] = featureBM25(queryStems, docId, "body", BM25Model);
				if (Double.isNaN(feature[5])) {
					featureExistDoc[5] = 0;
				}
			}
			if (featureOn[6] == 1) {
				// need to get Indri body feature
				feature[6] = featureIndri(queryStems, docId, "body", IndriModel);
				if (Double.isNaN(feature[6])) {
					featureExistDoc[6] = 0;
				}
//				System.out.print("feature 6: ");
//				System.out.println(feature[6]);
			}
			if (featureOn[7] == 1) {
				// need to get overlap body feature
				feature[7] = featureOverlap(queryStems, docId, "body");
				if (Double.isNaN(feature[7])) {
					featureExistDoc[7] = 0;
				}
			}
			if (featureOn[8] == 1) {
				// need to get BM25 title feature
				feature[8] = featureBM25(queryStems, docId, "title", BM25Model);
				if(Double.isNaN(feature[8]) ) {
					featureExistDoc[8] = 0;
				}
			}
			if (featureOn[9] == 1) {
				// need to get Indri title feature
				feature[9] = featureIndri(queryStems, docId, "title", IndriModel);
				if(Double.isNaN(feature[9])) {
					featureExistDoc[9] = 0;
				}
//				System.out.print("feature 9: ");
//				System.out.println(feature[9]);
			}
			if (featureOn[10] == 1) {
				// need to get overlap title feature
				feature[10] = featureOverlap(queryStems, docId, "title");
				if (Double.isNaN(feature[10])) {
					featureExistDoc[10] = 0;
				}
			}
			if (featureOn[11] == 1) {
				// need to get BM25 url feature
				feature[11] = featureBM25(queryStems, docId, "url", BM25Model);
				if (Double.isNaN(feature[11])) {
					featureExistDoc[11] = 0;
				}
			}
			if (featureOn[12] == 1) {
				// need to get Indri url feature
				feature[12] = featureIndri(queryStems, docId, "url", IndriModel);
				if (Double.isNaN(feature[12])) {
					featureExistDoc[12] = 0;
				}
//				System.out.print("feature 12: ");
//				System.out.println(feature[12]);
			}
			if (featureOn[13] == 1) {
				// need to get overlap url feature
				feature[13] = featureOverlap(queryStems, docId, "url");
				if (Double.isNaN(feature[13])) {
					featureExistDoc[13] = 0;
				}
			}
			if (featureOn[14] == 1) {
				// need to get BM25 inlink feature
				feature[14] = featureBM25(queryStems, docId, "inlink", BM25Model);
				if(Double.isNaN(feature[14])) {
					featureExistDoc[14] = 0;
				}
			}
			if (featureOn[15] == 1) {
				// need to get Indri inlink feature
				feature[15] = featureIndri(queryStems, docId, "inlink", IndriModel);
				if(Double.isNaN(feature[15])) {
					featureExistDoc[15] = 0;
				}
//				System.out.print("feature 15: ");
//				System.out.println(feature[15]);
			}
			if (featureOn[16] == 1) {
				// need to get overlap inlink feature
				feature[16] = featureOverlap(queryStems, docId, "inlink");
				if (Double.isNaN(feature[16])) {
					featureExistDoc[16] = 0;
				}
			}
			if (featureOn[17] == 1) {
				feature[17] = feature17(queryStems, docId, IndriModel);
			}
			if (featureOn[18] == 1) {
				feature[18] = featuer18(queryStems, docId, feature);
			}
			return feature;
		} catch (Exception e) {
			System.out.print("Catch exception. The external Id is: ");
			System.out.println(externalId);
			return null;
		}
	}

	
	
	private static double featureOverlap(List<String> queryStems, int docId, String field) throws IOException {
		int count = 0;
		int stemNum = queryStems.size();
		TermVector terms = new TermVector(docId, field);
		if (terms.positionsLength() == 0) {
			return Double.NaN;
		}
		for (int i = 0; i < stemNum; i++) {
			int indexOfTerm = terms.indexOfStem(queryStems.get(i));
			if (indexOfTerm > 0) {
				count++;
			}
		}
		return (double) count / (double) stemNum;
	}

	private static double featureBM25(List<String> queryStems, int docId, String field, RetrievalModel BM25Model)
			throws IOException {
		double featureScore = 0.0;
		TermVector terms = new TermVector(docId, field);
		if (terms.positionsLength() == 0) {
			return Double.NaN;
		}
		RetrievalModelBM25 BM25 = (RetrievalModelBM25) BM25Model;
		double k_1 = BM25.getK_1();
		double b = BM25.getB();
		double k_3 = BM25.getK_3();
		double N = (double) Idx.getNumDocs();
		double docCount = (double) Idx.getDocCount(field);
		double docLenD = (double) Idx.getFieldLength(field, docId);
		double totalDocLen = (double) Idx.getSumOfFieldLengths(field);
		for (int i = 0; i < queryStems.size(); i++) {
			int indexOfTerm = terms.indexOfStem(queryStems.get(i));
			if (indexOfTerm < 0) {
				continue;
			}
			double qtf = 1.0;
			double tf = (double) terms.stemFreq(indexOfTerm);
			double df = (double) terms.stemDf(indexOfTerm);
			double avgDocLen = totalDocLen / docCount;
			double idf = Math.max(0, Math.log((N - df + 0.5) / (df + 0.5)));
			double termWeight = tf / (tf + k_1 * ((1 - b) + b * docLenD / avgDocLen));
			double queryWeight = (k_3 + 1.0) * qtf / (k_3 + qtf);
			double termScore = idf * termWeight * queryWeight;
			featureScore = featureScore + termScore;
		}
		return featureScore;
	}

	private static double feature17(List<String> queryStems, int docId, RetrievalModel IndriModel) throws Exception {
		double featureScore = 0.0;
		TermVector terms = new TermVector(docId, "body");
		if (terms.positionsLength() == 0) {
			return Double.NaN;
		}
		RetrievalModelIndri Indri = (RetrievalModelIndri) IndriModel;
		double lambda = Indri.getLambda();
		double qtfMax = Double.MIN_VALUE;
		double totalDocLen = (double)Idx.getSumOfFieldLengths("body");
		double docCount = (double)Idx.getDocCount("body");
		double avgDocLen = totalDocLen / docCount;
		double docLen = (double)Idx.getFieldLength("body", docId);
		double N = (double)Idx.getNumDocs();
		// Get qtfMax	
		for(int i = 0; i < queryStems.size(); i++) {
			int indexOfTerm = terms.indexOfStem(queryStems.get(i));
			if (indexOfTerm < 0) {
				continue;
			}
			double tf = (double) terms.stemFreq(indexOfTerm);
			if(tf > qtfMax) {
				qtfMax = tf;
			}
		}
		// Calculate the score of the document
		for(int i = 0; i < queryStems.size(); i++) {
			int indexOfTerm = terms.indexOfStem(queryStems.get(i));
			if (indexOfTerm < 0) {
				continue;
			}
			double tf = (double)terms.stemFreq(indexOfTerm);
			double df = (double)terms.stemDf(indexOfTerm);
			double idf = Math.log((N + 1) / df);
			double queryTermWeight = (tf / qtfMax) * idf;
			double ctf = (double)terms.totalStemFreq(indexOfTerm);
			double pMLE = ctf / totalDocLen;
			double termScore = (1 - lambda) * (tf / (docLen / avgDocLen)) + lambda * pMLE;
			double score = queryTermWeight * termScore;
			featureScore = featureScore + score;
		}
		return featureScore;
	}
	
	private static double featuer18(List<String> queryStems, int docId, double[] feature) throws IOException {
		double featureScore = 0.0;
		TermVector termsBody = new TermVector(docId, "body");
		TermVector termsTitle = new TermVector(docId, "title");
		TermVector termsUrl = new TermVector(docId, "url");
		TermVector termsInlink = new TermVector(docId, "inlink");
		double totalDocLenBody = Idx.getSumOfFieldLengths("body");
		double totalDocLenTitle = Idx.getSumOfFieldLengths("title");
		double totalDocLenUrl = Idx.getSumOfFieldLengths("url");
		double totalDocLenInlink = Idx.getSumOfFieldLengths("inlink");
		double docCountBody = (double)Idx.getDocCount("body");
		double docCountTitle = (double)Idx.getDocCount("title");
		double docCountUrl = (double)Idx.getDocCount("url");
		double docCountInlink = (double)Idx.getDocCount("inlink");
		double docLenBody = (double)Idx.getFieldLength("body", docId);
		double docLenTitle = (double)Idx.getFieldLength("title", docId);
		double docLenUrl = (double)Idx.getFieldLength("url", docId);
		double docLenInlink = (double)Idx.getFieldLength("inlink", docId);
		double normDocLenBody = docLenBody / (totalDocLenBody / docCountBody);
		double normDocLenTitle = docLenTitle / (totalDocLenTitle / docCountTitle);
		double normDocLenUrl = docLenUrl / (totalDocLenUrl / docCountUrl);
		double normDouLenInlink = docLenInlink / (totalDocLenInlink / docCountInlink);
		double tfBody = 0.0;
		double tfTitle = 0.0;
		double tfUrl = 0.0;
		double tfInlink = 0.0;
		for(int i = 0; i < queryStems.size(); i++) {
			int indexOfTermBody = -1;
			int indexOfTermTitle = -1;
			int indexOfTermUrl = -1;
			int indexOfTermInlink = -1;
			if(termsBody.positionsLength() > 0) {
				indexOfTermBody = termsBody.indexOfStem(queryStems.get(i));
			}
			if(termsTitle.positionsLength() > 0) {
				termsTitle.indexOfStem(queryStems.get(i));
			}
			if(termsUrl.positionsLength() > 0) {
				indexOfTermUrl = termsUrl.indexOfStem(queryStems.get(i));
			}
			if(termsInlink.positionsLength() > 0) {
				indexOfTermInlink = termsInlink.indexOfStem(queryStems.get(i));
			}
			
			
			if(indexOfTermBody >= 0) {
				tfBody = tfBody + (double)termsBody.stemFreq(indexOfTermBody) / normDocLenBody;
			}
			if(indexOfTermTitle >= 0) {
				tfTitle = tfTitle + (double)termsTitle.stemFreq(indexOfTermTitle) / normDocLenTitle;
			}
			if(indexOfTermUrl >= 0) {
				tfUrl = tfUrl + (double)termsUrl.stemFreq(indexOfTermUrl) / normDocLenUrl;
			}
			if(indexOfTermInlink >= 0) {
				tfInlink = tfInlink + (double)termsInlink.stemFreq(indexOfTermInlink) / normDouLenInlink;
			}			
		}
		
		double tfTotal = tfBody + tfTitle + tfUrl + tfInlink;
		if(tfTotal <= 0) {
			return 0;
		}
		double bodyWeight = tfBody / tfTotal;
		double titleWeight = tfTitle / tfTotal;
		double urlWeight = tfUrl / tfTotal;
		double inlinkWeight = tfInlink / tfTotal;
		
		double fieldScoreTotal = 0;
		if(!Double.isNaN(feature[5])) {
			fieldScoreTotal = fieldScoreTotal + feature[5];
		}
		if(!Double.isNaN(feature[8])) {
			fieldScoreTotal = fieldScoreTotal + feature[8];
		}
		if(!Double.isNaN(feature[11])) {
			fieldScoreTotal = fieldScoreTotal + feature[11];
		}
		if(!Double.isNaN(feature[14])) {
			fieldScoreTotal = fieldScoreTotal + feature[14];
		}
		if(fieldScoreTotal <= 0) {
			return 0;
		}
		
		if(!Double.isNaN(feature[5])) {
			featureScore = featureScore + bodyWeight * feature[5] / fieldScoreTotal;
		}
		if(!Double.isNaN(feature[8])) {
			featureScore = featureScore + titleWeight * feature[8] / fieldScoreTotal;
		}
		if(!Double.isNaN(feature[11])) {
			featureScore = featureScore + urlWeight * feature[11] / fieldScoreTotal;
		}
		if(!Double.isNaN(feature[14])) {
			featureScore = featureScore + inlinkWeight * feature[14] / fieldScoreTotal;
		}
		return Math.log(featureScore);
	}
	
	private static double featureIndri(List<String> queryStems, int docId, String field, RetrievalModel IndriModel)
			throws IOException {
		double featureScore = 1.0;
		TermVector terms = new TermVector(docId, field);
		if (terms.positionsLength() == 0) {
			return Double.NaN;
		}
		RetrievalModelIndri Indri = (RetrievalModelIndri) IndriModel;
		double mu = Indri.getMu();
		double lambda = Indri.getLambda();
		double lengthTokens = (double) Idx.getSumOfFieldLengths(field);
		double docLen = (double) Idx.getFieldLength(field, docId);
		boolean TermMatch = false;
		for (int i = 0; i < queryStems.size(); i++) {
			int indexOfTerm = terms.indexOfStem(queryStems.get(i));
			double tf;
			double ctf;
			if (indexOfTerm < 0) {
				tf = 0.0;
				ctf = Idx.getTotalTermFreq(field, queryStems.get(i));
			} else {
				tf = (double) terms.stemFreq(indexOfTerm);
				ctf = (double)terms.totalStemFreq(indexOfTerm);
				TermMatch = true;
			}
			double p_MLE_Qi_C = ctf / lengthTokens;
			double p_MLE_Qi_d = (tf + mu * p_MLE_Qi_C) / (docLen + mu);
			double termScore = (1 - lambda) * p_MLE_Qi_d + lambda * p_MLE_Qi_C;
			double q_abs_inverse = 1.0 / (double)queryStems.size();
			termScore = Math.pow(termScore, q_abs_inverse);
			featureScore = featureScore * termScore;
		}
		if (TermMatch == false) {
			return 0.0;
		}
		
		return featureScore;
	}

	private static int countNumOfChar(String s, char ch) {
		int count = 0;
		int len = s.length();
		for (int i = 0; i < len; i++) {
			if (s.charAt(i) == ch) {
				count++;
			}
		}
		return count;
	}

	@SuppressWarnings("resource")
	private static List<String> readQrelFile(String filepath) throws IOException {
		System.out.println("In function readQrelFile");
		List<String> qrelStrs = new ArrayList<>();
		BufferedReader input = null;
		input = new BufferedReader(new FileReader(filepath));
		String qrelLine = null;
		while ((qrelLine = input.readLine()) != null) {
			qrelStrs.add(qrelLine);
		}
		return qrelStrs;
	}

	private static List<String> getQueryTerms(String query) throws IOException {
		System.out.println("In function getQueryTerms");
		List<String> queryTerms = new ArrayList<String>();
		while (query.length() > 0) {
			String[] substrings = query.split("[ \\t\\n\\r]+", 2);
			String token = substrings[0];

			String t[] = QryParser.tokenizeString(token);
			for (int j = 0; j < t.length; j++) {
				queryTerms.add(t[j]);
			}

			if (substrings.length < 2) {
				query = "";
			} else {
				query = substrings[1];
			}
		}
		return queryTerms;
	}

	private static void disableFeature(String disableFeatureStr, int[] featureOn) {
		System.out.println("In function dsableFeature");
		while (disableFeatureStr.length() > 0) {
			int index = disableFeatureStr.indexOf(',');
			int featureNum;
			if (index < 0) {
				featureNum = Integer.parseInt(disableFeatureStr);
				disableFeatureStr = "";
			} else {
				String[] subString = disableFeatureStr.split(",", 2);
				featureNum = Integer.parseInt(subString[0]);
				disableFeatureStr = subString[1];
			}
			featureOn[featureNum] = 0;
		}
	}


	private static RetrievalModel initializeLToRRetrievalModel(Map<String, String> parameters, String modelName)
			throws IOException {
		RetrievalModel model = null;

		if (modelName.equals("BM25")) {
			String k_1 = parameters.get("BM25:k_1");
			String b = parameters.get("BM25:b");
			String k_3 = parameters.get("BM25:k_3");
			model = new RetrievalModelBM25(k_1, b, k_3);
		} else if (modelName.equals("Indri")) {
			String mu = parameters.get("Indri:mu");
			String lambda = parameters.get("Indri:lambda");
			model = new RetrievalModelIndri(mu, lambda);
		} else {
			throw new IllegalArgumentException("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
		}

		return model;
	}

	/**
	 * Print the query results.
	 * 
	 * THIS IS NOT THE CORRECT OUTPUT FORMAT. YOU MUST CHANGE THIS METHOD SO THAT IT
	 * OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK PAGE, WHICH IS:
	 * 
	 * QueryID Q0 DocID Rank Score RunID
	 * 
	 * @param queryName
	 *            Original query.
	 * @param result
	 *            A list of document ids and scores
	 * @throws IOException
	 *             Error accessing the Lucene index.
	 */
	static void printResults(String queryName, ScoreList result, BufferedWriter writer, String reaultLen)
			throws IOException {

		DecimalFormat formatter = new DecimalFormat("#.000000000000000000");

		System.out.println(queryName + ":  ");
		if (result.size() < 1) {
			writer.write(queryName + " Q0 " + "dummyRecord 1 0 fubar\n");
		} else {
			System.out.println("result size is " + result.size());
			int temp = Integer.parseInt(reaultLen);
			int resLen = temp < result.size() ? temp : result.size();
			for (int i = 0; i < resLen; i++) {
				writer.write(queryName + " Q0 " + Idx.getExternalDocid(result.getDocid(i)) + " " + (i + 1) + " "
						+ formatter.format(result.getDocidScore(i)) + " fubar\n");
			}
			// writer.write("\r\n");

		}
		System.out.println(queryName + ":  print result end");
	}
	
	/**
	 * Print the query results for diversify ranking
	 * @throws IOException 
	 * */
	static void printResultsDiversity(String queryName, ScoreList result, BufferedWriter writer, String resultLen) throws IOException {
		DecimalFormat formatter = new DecimalFormat("#.000000000000000000");
		System.out.println(queryName + ": ");
		if (result.size() < 1) {
			writer.write(queryName + " Q0 " + "dummyRecord 1 0 fubar\n");
		} else {
			System.out.println("result size is " + result.size());
			int temp = Integer.parseInt(resultLen);
			int resLen = temp < result.size() ? temp : result.size();
			for (int i = 0; i < resLen; i++) {
				writer.write(queryName + " Q0 " + Idx.getExternalDocid(result.getDocid(i)) + " " + (i + 1) + " "
						+ formatter.format(result.getDocidScore(i)) + " reference\n");
			}
		}
	}
	
	static void printResultsSVM(List<String> qid, List<List<String>> tempResult, Map<String, String> parameters) throws Exception, IOException {
		System.out.println("In function printResult");
		
		List<Double> SVMScore = null;
		SVMScore = readSVMScore(parameters.get("letor:testingDocumentScores"));
		
		BufferedWriter resultWriter = new BufferedWriter(new FileWriter(parameters.get("trecEvalOutputPath")));
		
		int qryNum = qid.size();
		
		int SVMScoreIndex = 0;
		
		for(int i = 0; i < qryNum; i++) {
			List<String> docsExternalId = tempResult.get(i);
			int docNum = docsExternalId.size();
			ScoreList r = new ScoreList();
			if(i == (qryNum - 1)) {
				System.out.println("docNum is: ");
			}
			for(int j = 0; j < docNum; j++) {
				
				String externalId = docsExternalId.get(j);
				int docid = Idx.getInternalDocid(externalId);
				double docScore = SVMScore.get(SVMScoreIndex);
				r.add(docid, docScore);
				SVMScoreIndex++;
			}
			
			r.sort();
			printSVMResultHelper(qid.get(i), r, resultWriter, "100");
		}
		
		resultWriter.flush();
		resultWriter.close();
	}
	
	static void printSVMResultHelper(String queryName, ScoreList result, BufferedWriter writer, String reaultLen) throws IOException {
		DecimalFormat formatter = new DecimalFormat("#0.000000000000");

		System.out.println(queryName + ":  ");
		if (result.size() < 1) {
			writer.write(queryName + " Q0 " + "dummyRecord 1 0 yubinletor\n");
		} else {
			System.out.println("result size is " + result.size());
			int temp = Integer.parseInt(reaultLen);
			int resLen = temp < result.size() ? temp : result.size();
			for (int i = 0; i < resLen; i++) {
				writer.write(queryName + " Q0 " + Idx.getExternalDocid(result.getDocid(i)) + " " + (i + 1) + " "
						+ formatter.format(result.getDocidScore(i)) + " yubinletor\n");
			}
			// writer.write("\r\n");
			System.out.println(queryName + ":  print SVM result end");
		}
	}
	
	static List<Double> readSVMScore(String filePath) throws NumberFormatException, IOException {
		System.out.println("In readSVMScore function");
		BufferedReader input = new BufferedReader(new FileReader(filePath));
		
		List<Double> SVMScore = new ArrayList<>();
		
		String sLine = null;
		
		while((sLine = input.readLine()) != null) {
			double score = Double.parseDouble(sLine);
			SVMScore.add(score);
		}
		System.out.print("The size of SVMSore is: ");
		System.out.println(SVMScore.size());
		return SVMScore;
	}
	
	static void printFeatures(BufferedWriter featureWriter, List<double[]> features, int[] featureOn,
			List<String> target, String qid, List<String> externalId, int featureNum) throws IOException {
		int docNum = features.size();
		int arrayLen = featureNum + 1;
		for (int i = 0; i < docNum; i++) {
			featureWriter.write(target.get(i) + " " + "qid:" + qid + " ");
			double[] feature = features.get(i);
			for (int j = 1; j < arrayLen; j++) {
				if (featureOn[j] == 1) {
					featureWriter.write(Integer.toString(j) + ":" + Double.toString(feature[j]) + " ");
				}
			}
			featureWriter.write("#" + externalId.get(i) + "\n");
		}
	}

	/**
	 * Read the specified parameter file, and confirm that the required parameters
	 * are present. The parameters are returned in a HashMap. The caller (or its
	 * minions) are responsible for processing them.
	 * 
	 * @return The parameters, in <key, value> format.
	 */
	private static Map<String, String> readParameterFile(String parameterFileName) throws IOException {

		Map<String, String> parameters = new HashMap<String, String>();

		System.out.println("parameterFileName is " + parameterFileName);
		File parameterFile = new File(parameterFileName);

		if (!parameterFile.exists()) {
			System.out.println("param0.txt does not exists");
		}

		if (!parameterFile.canRead()) {
			throw new IllegalArgumentException("Can't read " + parameterFileName);
		}

		Scanner scan = new Scanner(parameterFile);
		String line = null;
		do {
			line = scan.nextLine();
			String[] pair = line.split("=");
			parameters.put(pair[0].trim(), pair[1].trim());
		} while (scan.hasNext());

		scan.close();
		if(parameters.containsKey("diversity") && parameters.get("diversity").equals("true")) {
			if (!(parameters.containsKey("indexPath") && parameters.containsKey("queryFilePath")
					&& parameters.containsKey("trecEvalOutputPath"))) {
				throw new IllegalArgumentException("Required parameters were missing from the parameter file.");
			}
		} else 
		{ 
			if(!(parameters.containsKey("indexPath") && parameters.containsKey("queryFilePath")
				&& parameters.containsKey("trecEvalOutputPath") && parameters.containsKey("retrievalAlgorithm"))) {
			throw new IllegalArgumentException("Required parameters were missing from the parameter file.");
			}
			if (parameters.get("retrievalAlgorithm").equals("BM25")) {
				if (!(parameters.containsKey("BM25:k_1") && parameters.containsKey("BM25:b")
						&& parameters.containsKey("BM25:k_3"))) {
					throw new IllegalArgumentException("Required parameters were missing from the parameter file.");
				}
			}
			if (parameters.get("retrievalAlgorithm").equals("Indri")) {
				if (!(parameters.containsKey("Indri:mu") && (parameters.containsKey("Indri:lambda")))) {
					throw new IllegalArgumentException("Required parameters were missing from the parameter file.");
				}
			}
			if (parameters.get("retrievalAlgorithm").equals("letor")) {

			}
		}
		
		return parameters;
	}

}
