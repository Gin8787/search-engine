import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QryIopWindow extends QryIop {
	
	private int distance = 0;
	
	static private final int INVALIDLOC = Integer.MIN_VALUE;
	
	static private final int MAXLOC = Integer.MAX_VALUE;
	
	QryIopWindow(int distance) {
		this.distance = distance;
	}
	

	@Override
	protected void evaluate() throws IOException {
		this.invertedList = new InvList(this.getField());
		
		if (args.size() == 0) {
			return;
		}
				
		while (this.docIteratorHasMatchAll(null)) {		
			int docId_0 = this.args.get(0).docIteratorGetMatch();
			
//			String externalId = Idx.getExternalDocid(docId_0);
//			if(externalId.equals("GX069-07-13936812")) {
//				System.out.println("Start to debug");
//			}
			
			List<Integer> positions = new ArrayList<Integer>();
			
			boolean moveToNextDoc = false;
			while(true) {
				int maxLoc = Integer.MIN_VALUE;
				int minLoc = Integer.MAX_VALUE;
				int minLocIndex = -1;
				for(int i = 0; i < this.args.size(); i++) {
					QryIop q_i = (QryIop)this.args.get(i);
					if(!q_i.locIteratorHasMatch()) {
						maxLoc = Integer.MIN_VALUE;
						moveToNextDoc = true;
						break;
					}
					int q_iLoc = q_i.locIteratorGetMatch();
					if(q_iLoc < minLoc) {
						minLoc = q_iLoc;
						minLocIndex = i;
					}
					if(q_iLoc > maxLoc) {
						maxLoc = q_iLoc;
					}
				}	
					// run out of locations, move doc Iteration
				if(moveToNextDoc) {
					break;
				}
					
				//Location not match, move the locIterator with the smallest value
				if(Math.abs((double)maxLoc - (double)minLoc) >= distance) {
					QryIop q_advance = (QryIop)this.args.get(minLocIndex);
					q_advance.locIteratorAdvance();
				} else {
					positions.add(maxLoc);
					for (int i = 0; i < this.args.size(); i++) {
						QryIop q_i = (QryIop) this.args.get(i);
						q_i.locIteratorAdvance();
					}
				}
			}
			if(!positions.isEmpty()) {
				Collections.sort(positions);
				this.invertedList.appendPosting(docId_0, positions);
			}
			this.args.get(0).docIteratorAdvancePast(docId_0);
		}
	}
	

}
