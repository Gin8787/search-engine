
public class RetrievalModelIndri extends RetrievalModel{
	private double mu;
	
	private double lambda;
	
	public RetrievalModelIndri() {
		this.mu = 2500;
		this.lambda = 0.2;
	}
	
	public RetrievalModelIndri(String mu, String lambda) {
		this.mu = Double.parseDouble(mu);
		this.lambda = Double.parseDouble(lambda);
	}
	
	public double getMu() {
		return this.mu;
	}
	
	public double getLambda() {
		return this.lambda;
	}
	
	@Override
	public String defaultQrySopName() {
		return new String("#and");
	}
	
	
}
