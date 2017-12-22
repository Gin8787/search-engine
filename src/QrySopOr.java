/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 * The OR operator for all retrieval models.
 */
public class QrySopOr extends QrySop {

	/**
	 * Indicates whether the query has a match.
	 * 
	 * @param r
	 *            The retrieval model that determines what is a match
	 * @return True if the query matches, otherwise false.
	 */
	public boolean docIteratorHasMatch(RetrievalModel r) {
		return this.docIteratorHasMatchMin(r);
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
	public double getScore (RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.getScoreUnrankedBoolean (r);
    } else if(r instanceof RetrievalModelRankedBoolean) {
    	return this.getScoreRankedBoolean(r);
    } else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the OR operator.");
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
		double docIdMinScore = Double.MIN_VALUE;
		for (int i = 0; i < this.args.size(); i++) {
			QrySop q_i = (QrySop) this.args.get(i);

			if (q_i.docIteratorHasMatch(r)) {
				int q_iDocid = q_i.docIteratorGetMatch();
				if(q_iDocid == docIdMin) {
					double q_iDocScore = q_i.getScore(r);
					if(q_iDocScore > docIdMinScore) {
						docIdMinScore = q_iDocScore;
					}
				} else {
//					System.out.print("docIdMin misMatch");
				}
			}
		}
		return docIdMinScore;
	}

	@Override
	public double getDefaultScore(RetrievalModel r, int docIdMin) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}
}
