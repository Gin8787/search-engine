import java.io.IOException;

public class QrySopSum extends QrySop{

	@Override
	public double getScore(RetrievalModel r) throws IOException {
		if (r instanceof RetrievalModelBM25) {
		      return this.getScoreBM25(r);
		} else {
		      throw new IllegalArgumentException
		        (r.getClass().getName() + " doesn't support the OR operator.");
		}  
	}

	@Override
	public boolean docIteratorHasMatch(RetrievalModel r) {
		return this.docIteratorHasMatchMin(r);
	}
	
	public double getScoreBM25(RetrievalModel r) throws IOException {
		int docIdMin = this.docIteratorGetMatch();
		double docIdScore = 0;
		for (int i = 0; i < this.args.size(); i++) {
			QrySop q_i = (QrySop) this.args.get(i);
			
			if (q_i.docIteratorHasMatch(r)) {
				int q_iDocId = q_i.docIteratorGetMatch();
				if(q_iDocId == docIdMin) {
					double q_iDocScore = q_i.getScore(r);
					docIdScore = docIdScore + q_iDocScore;
				}
			}
		}
		return docIdScore;
	}

	@Override
	public double getDefaultScore(RetrievalModel r, int docIdMin) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

}
