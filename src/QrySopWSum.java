import java.io.IOException;
import java.util.ArrayList;

public class QrySopWSum extends QrySop{
	
	ArrayList<String> weightTmp;
	ArrayList<Double> weights;
	
	public QrySopWSum() {
		this.weightTmp = new ArrayList<String>();
		this.weights = new ArrayList<Double>();
	}

	@Override
	public double getScore(RetrievalModel r) throws IOException {
		if (r instanceof RetrievalModelIndri) {
			return this.getScoreIndri(r);
		} else {
			throw new IllegalArgumentException
	        (r.getClass().getName() + " doesn't support the OR operator.");
		}
	}

	@Override
	public double getDefaultScore(RetrievalModel r, int docIdMin) throws IOException {
		if (r instanceof RetrievalModelIndri) {
			return this.getDefaultScoreIndri(r, docIdMin);
		} else {
			throw new IllegalArgumentException
	        (r.getClass().getName() + " doesn't support the OR operator.");
		}
	}
	
	@Override
	public boolean docIteratorHasMatch(RetrievalModel r) {
		return this.docIteratorHasMatchMin(r);
	}
	
	public double getScoreIndri(RetrievalModel r) throws IOException {
		int docIdMin = this.docIteratorGetMatch();
		double score = 0;
		double totalWeight = this.processWeights();
		
//		String externalId = Idx.getExternalDocid(docIdMin);
//		if(externalId.equals("GX087-45-11201910")) {
//			System.out.println("Start to debug");
//		}
		
		for (int i = 0; i < this.args.size(); i++) {
			QrySop q_i = (QrySop) this.args.get(i);
			double q_iDocScore = 0;
			if(q_i.docIteratorHasMatch(r)) {
				int q_iDocId = q_i.docIteratorGetMatch();
				
				if (q_iDocId == docIdMin) {
					q_iDocScore = q_i.getScore(r);
				} else {
					q_iDocScore = q_i.getDefaultScore(r, docIdMin);
				}
			} else {
				q_iDocScore = q_i.getDefaultScore(r, docIdMin);
			}
			double weightFraction = this.weights.get(i) / totalWeight;
			score = score + weightFraction * q_iDocScore;
		}
		return score;
	}
	
	private double getDefaultScoreIndri(RetrievalModel r, int docIdMin) throws IOException {
		double score = 0;
		double totalWeight = this.processWeights();
		for(int i = 0; i < this.args.size(); i++) {
			QrySop q_i = (QrySop) this.args.get(i);
			double q_iDocScore = q_i.getDefaultScore(r, docIdMin);
			double weightFraction = this.weights.get(i) / totalWeight;
			score = score + weightFraction * q_iDocScore;
		}
		return score;
	}
	
	private double processWeights() {
		double totalWeights = 0.0;
		for(int i = 0; i < this.weightTmp.size(); i++) {
			double weight = Double.parseDouble(this.weightTmp.get(i));
			this.weights.add(weight);
			totalWeights = totalWeights + weight;
		}
		return totalWeights;
	}
	
	public void appendWeights(String weight) {
		this.weightTmp.add(weight);
	}

	
}
