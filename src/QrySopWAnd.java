import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class QrySopWAnd extends QrySop {
	
	private ArrayList<String> weightTmp;
	private ArrayList<Double> weights;
	
	public QrySopWAnd() {
		this.weights = new ArrayList<Double>();
		this.weightTmp = new ArrayList<String>();
	}
	
	@Override
	public double getScore(RetrievalModel r) throws IOException {
		if (r instanceof RetrievalModelIndri) {
			return this.getScoreIndri(r);
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
	@Override
	public boolean docIteratorHasMatch(RetrievalModel r) {
		if (r instanceof RetrievalModelIndri) {
			return this.docIteratorHasMatchMin(r);
		} else {
			throw new IllegalArgumentException(r.getClass().getName()
					+ " doesn't support the OR operator.");
		}
	}
	
	private double getScoreIndri(RetrievalModel r) throws IOException {
		int docIdMin = this.docIteratorGetMatch();
		double score = 1.0;
		double totalWeight = this.processWeights();
		
		for(int i = 0; i < this.args.size(); i++) {
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
			double weight_inverse = this.weights.get(i) / totalWeight;
			score = score * Math.pow(q_iDocScore, weight_inverse);
		}
		
		return score;
	}
	
	private double getDefaultScoreIndri(RetrievalModel r, int docIdMin) throws IOException {
		double score = 1.0;
		double totalWeight = this.processWeights();
		for(int i = 0; i < this.args.size(); i++) {
			QrySop q_i = (QrySop) this.args.get(i);
			double q_iDocScore = q_i.getDefaultScore(r, docIdMin);
			
			double weight_inverse = this.weights.get(i) / totalWeight;
			score = score * Math.pow(q_iDocScore, weight_inverse);
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
