import java.io.IOException;

public class QrySopAnd extends QrySop {
	
	/**
	   *  Get a score for the document that docIteratorHasMatch matched.
	   *  @param r The retrieval model that determines how scores are calculated.
	   *  @return The document score.
	   *  @throws IOException Error accessing the Lucene index
	   */
	@Override
	public double getScore(RetrievalModel r) throws IOException {
		if (r instanceof RetrievalModelUnrankedBoolean) {
			return this.getScoreUnrankedBoolean(r);
		} else if(r instanceof RetrievalModelRankedBoolean) {
			return this.getScoreRankedBoolean(r);
		} else if(r instanceof RetrievalModelIndri) {
			return this.getScoreIndri(r);
		} else {
			throw new IllegalArgumentException(r.getClass().getName()
					+ " doesn't support the OR operator.");
		}
	}
	
	/**
	   *  Indicates whether the query has a match.
	   *  @param r The retrieval model that determines what is a match
	   *  @return True if the query matches, otherwise false.
	   */
	@Override
	public boolean docIteratorHasMatch(RetrievalModel r) {
		if (r instanceof RetrievalModelUnrankedBoolean) {
			return this.docIteratorHasMatchAll(r);
		} else if (r instanceof RetrievalModelRankedBoolean) {
			return this.docIteratorHasMatchAll(r);
		} else if (r instanceof RetrievalModelIndri) {
			return this.docIteratorHasMatchMin(r);
		} else {
			throw new IllegalArgumentException(r.getClass().getName()
					+ " doesn't support the OR operator.");
		}
		
	}
	
	@Override
	public double getDefaultScore(RetrievalModel r, int docIdMin) throws IOException {
		if (r instanceof RetrievalModelIndri) {
			return this.getDefaultScoreIndri(r, docIdMin);
		} else {
			throw new IllegalArgumentException(r.getClass().getName()
					+ " doesn't support the OR operator.");
		}
	}
	
	/**
	 * getScore for the UnrankedBoolean retrieval model.
	 * 
	 * @param r
	 *            The retrieval model that determines how scores are calculated.
	 * @return The document score.
	 * @throws IOException
	 *             Error accessing the Lucene index
	 */
	private double getScoreUnrankedBoolean(RetrievalModel r) throws IOException {
		if (!this.docIteratorHasMatchCache()) {
			return 0.0;
		} else {
			return 1.0;
		}
	}
	
	
	
	private double getScoreRankedBoolean(RetrievalModel r) throws IOException {
		int docIdMin = this.docIteratorGetMatch();
		double docIdMinScore = Double.MAX_VALUE;
		for (int i = 0; i < this.args.size(); i++) {
			QrySop q_i = (QrySop) this.args.get(i);
			
			if (q_i.docIteratorHasMatch(r)) {
				int q_iDocid = q_i.docIteratorGetMatch();
				if(q_iDocid == docIdMin) {
					double q_iDocScore = q_i.getScore(r);
					if(q_iDocScore < docIdMinScore) {
						docIdMinScore = q_iDocScore;
					}
				}
			}
		}
		return docIdMinScore;
	}
	
	private double getScoreIndri(RetrievalModel r) throws IOException {
		int docIdMin = this.docIteratorGetMatch();
		double score = 1.0;
//		String externalId = Idx.getExternalDocid(docIdMin);
//		if(externalId.equals("GX030-99-11423007")) {
//			System.out.println("Start to debug");
//		}
		for(int i = 0; i < this.args.size(); i++) {
			QrySop q_i = (QrySop) this.args.get(i);
			double q_iDocScore = 0;
			if (q_i.docIteratorHasMatch(r)) {
				int q_iDocId = q_i.docIteratorGetMatch();
				
				if (q_iDocId == docIdMin) {
					q_iDocScore = q_i.getScore(r);
				} else {
					q_iDocScore = q_i.getDefaultScore(r, docIdMin);
				}			
			} else {
				q_iDocScore = q_i.getDefaultScore(r, docIdMin);
			}
			double q_abs_inverse = 1.0 / (double)this.args.size();
			score = score * Math.pow(q_iDocScore, q_abs_inverse);
		}
		
		return score;
	}

	
	private double getDefaultScoreIndri(RetrievalModel r, int docIdMin) throws IOException {
		double score = 1.0;
		for(int i = 0; i < this.args.size(); i++) {
			QrySop q_i = (QrySop)this.args.get(i);
			
			double q_iDocScore = q_i.getDefaultScore(r, docIdMin);
			
			double q_abs_inverse = 1.0 / (double)this.args.size();
			score = score * Math.pow(q_iDocScore, q_abs_inverse);
		}
		return score;
	}
	
}
