
public class RetrievalModelBM25 extends RetrievalModel {
	
	private double k_1;
	
	private double b;
	
	private double k_3;
	
	public RetrievalModelBM25() {
		this.k_1 = 1.2;
		this.b = 0.75;
		this.k_3 = 500;
	}
	
	public RetrievalModelBM25(int k1, int b, int k3) {
		this.k_1 = k1;
		this.b = b;
		this.k_3 = k3;
	}
	
	public RetrievalModelBM25(String k1, String b, String k3) {
		this.k_1 = Double.parseDouble(k1);
		this.b = Double.parseDouble(b);
		this.k_3 = Double.parseDouble(k3);
	}
	
	@Override
	public String defaultQrySopName() {
		return new String("#sum");
	}
	
	public double getK_1() {
		return this.k_1;
	}
	
	public double getB() {
		return this.b;
	}
	
	public double getK_3() {
		return this.k_3;
	}
}
