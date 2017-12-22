/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 * The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

	/**
	 * Document-independent values that should be determined just once. Some
	 * retrieval models have these, some don't.
	 */

	/**
	 * Indicates whether the query has a match.
	 * 
	 * @param r
	 *            The retrieval model that determines what is a match
	 * @return True if the query matches, otherwise false.
	 */
	public boolean docIteratorHasMatch(RetrievalModel r) {
		return this.docIteratorHasMatchFirst(r);
	}

	/**
	 * Get a score for the document that docIteratorHasMatch matched.
	 * 
	 * @param r
	 *            The retrieval model that determines how scores are calculated.
	 * @return The document score.
	 * @throws IOException
	 *             Error accessing the Lucene index
	 */
	public double getScore(RetrievalModel r) throws IOException {

		if (r instanceof RetrievalModelUnrankedBoolean) {
			return this.getScoreUnrankedBoolean(r);
		} else if(r instanceof RetrievalModelRankedBoolean) {
			return this.getScoreRankedBoolean(r);
		} else if(r instanceof RetrievalModelBM25) {
			return this.getScoreBM25(r);
		} else if(r instanceof RetrievalModelIndri) {
			return this.getScoreIndri(r);
		} else {
			throw new IllegalArgumentException(r.getClass().getName()
					+ " doesn't support the SCORE operator.");
		}
	}
	

	public double getDefaultScore(RetrievalModel r, int docid1) throws IOException {
		if (r instanceof RetrievalModelIndri) {
			return this.getDefaultScoreIndri(r, docid1);
		} else {
			throw new IllegalArgumentException(r.getClass().getName()
					+ " doesn't support the SCORE operator.");
		}
	}
	
	/**
	 * getScore for the Unranked retrieval model.
	 * 
	 * @param r
	 *            The retrieval model that determines how scores are calculated.
	 * @return The document score.
	 * @throws IOException
	 *             Error accessing the Lucene index
	 */
	public double getScoreUnrankedBoolean(RetrievalModel r) throws IOException {
		if (!this.docIteratorHasMatchCache()) {
			return 0.0;
		} else {
			return 1.0;
		}
	}
	
	public double getScoreRankedBoolean(RetrievalModel r) throws IOException {
		QryIop query = (QryIop) this.args.get(0);
		InvList.DocPosting posting = query.docIteratorGetMatchPosting();
		return (double)posting.tf;
	}

	/**
	 * getScore for the BM25 retrieval model.
	 * 
	 * @param r
	 * 			The retrieval model that determines how scores are calculated.
	 * @return The document score.
	 * @throws IOExceptrion
	 * 				Error accessing the Lucene index
	 * */
	public double getScoreBM25(RetrievalModel r) throws IOException {
		RetrievalModelBM25 BM25 = (RetrievalModelBM25)r;
		double k_1 = BM25.getK_1();
		double b = BM25.getB();
		double k_3 = BM25.getK_3();
		double qtf = 1;
		QryIop query = (QryIop)this.args.get(0);
		InvList.DocPosting posting = query.docIteratorGetMatchPosting();
		double tfTD = (double)posting.tf;
		int docId = posting.docid;
		double dfT = (double)query.invertedList.df;
		double N = (double)Idx.getNumDocs();
		double docCount = (double)Idx.getDocCount(query.field);
		double docLenD = (double)Idx.getFieldLength(query.field, docId);
		double totalDocLen = (double)Idx.getSumOfFieldLengths(query.field);
		double avgDocLen = totalDocLen / docCount;
		double idf = Math.max(0, Math.log((N - dfT + 0.5) / (dfT + 0.5)));
		double termWeight = tfTD / (tfTD + k_1 * ((1 - b) + b * docLenD / avgDocLen));
		double queryWeight = (k_3 + 1) * qtf / (k_3 + qtf);
		return idf * termWeight * queryWeight;
	}
	
	/**
	 * getScore for the Indri retrieval model.
	 * 
	 * @param r
	 * 			The retrieval model that determines how scores are calculated.
	 * @return The document score.
	 * @throws IOExceptrion
	 * 				Error accessing the Lucene index
	 * */
	public double getScoreIndri(RetrievalModel r) throws IOException {
		RetrievalModelIndri Indri = (RetrievalModelIndri)r;
		double mu = Indri.getMu();
		double lambda = Indri.getLambda();
		QryIop query = (QryIop)this.args.get(0);
		InvList.DocPosting posting = query.docIteratorGetMatchPosting();
		double tf = (double)posting.tf;
		int docId = posting.docid;
		double lengthTokens = (double)Idx.getSumOfFieldLengths(query.field);
		double docLen = (double)Idx.getFieldLength(query.field, docId);
		double ctf = (double)query.invertedList.ctf;	
		double p_MLE_Qi_C = ctf / lengthTokens;
		double p_MLE_Qi_d = (tf + mu * p_MLE_Qi_C) / (docLen + mu);
		double score = (1 - lambda) * p_MLE_Qi_d + lambda * p_MLE_Qi_C;
		return score;
	}
	
	public double getDefaultScoreIndri(RetrievalModel r, int docid1) throws IOException {
//		int docIdMin = this.docIteratorGetMatch();
//		
//		String externalId = Idx.getExternalDocid(docIdMin);
//		if(externalId.equals("GX087-45-11201910")) {
//			System.out.println("Start to debug");
//		}
		
		RetrievalModelIndri Indri = (RetrievalModelIndri)r;
		double mu = Indri.getMu();
		double lambda = Indri.getLambda();
		QryIop query = (QryIop)this.args.get(0);
		double defaultTf = 0;
		double lengthTokens = (double)Idx.getSumOfFieldLengths(query.field);
		double docLen = (double)Idx.getFieldLength(query.field, docid1);
		double ctf = (double)query.invertedList.ctf;
		double p_MLE_Qi_C = ctf / lengthTokens;
		double p_MLE_Qi_d = (defaultTf + mu * p_MLE_Qi_C) / (docLen + mu);
		double score = (1 - lambda) * p_MLE_Qi_d + lambda * p_MLE_Qi_C;
		return score;
	}
	
	/**
	 * Initialize the query operator (and its arguments), including any internal
	 * iterators. If the query operator is of type QryIop, it is fully
	 * evaluated, and the results are stored in an internal inverted list that
	 * may be accessed via the internal iterator.
	 * 
	 * @param r
	 *            A retrieval model that guides initialization
	 * @throws IOException
	 *             Error accessing the Lucene index.
	 */
	public void initialize(RetrievalModel r) throws IOException {

		Qry q = this.args.get(0);
		q.initialize(r);
	}
	
	public int getTermFrequency(int docId) {
		QryIopTerm term = (QryIopTerm) this.args.get(0);
	    return term.invertedList.getTf(docId);
	}

}
