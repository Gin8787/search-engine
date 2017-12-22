import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QryIopNear extends QryIop {

	private int distance = 0;

	static private final int INVALIDLOC = Integer.MIN_VALUE;

	QryIopNear(int distance) {
		this.distance = distance;
	}

	@Override
	protected void evaluate() throws IOException {
		this.invertedList = new InvList(this.getField());

		if (args.size() == 0) {
			return;
		}
		
		//Find documents that contain all terms
		while (this.docIteratorHasMatchAll(null)) {
//			System.out.println("while this.docIteratorHasMatchAll(null)");

			int docId_0 = this.args.get(0).docIteratorGetMatch();
			int docId_1 = this.args.get(1).docIteratorGetMatch();

			String externalId = Idx.getExternalDocid(docId_0);

//			System.out
//					.printf("The same file is: %d and %d\n", docId_0, docId_1);

			int argsLen = this.args.size();

			List<Integer> positions = new ArrayList<Integer>();

//			if ((externalId.equals("GX230-38-15084879")) || (docId_0 == 4779)) {
//				System.out.println("Strange");
//				InvList.DocPosting posting0 = ((QryIop) this.args.get(0))
//						.docIteratorGetMatchPosting();
//				InvList.DocPosting posting1 = ((QryIop) this.args.get(1))
//						.docIteratorGetMatchPosting();
//
//				int postLen0 = posting0.positions.size();
//				int postLen1 = posting1.positions.size();
//
//				System.out.println("The locations of posting 0");
//				System.out.println(posting0.positions.toString());
//
//				System.out.println("The locations of posting 1");
//				System.out.println(posting1.positions.toString());
//
//			}

			while (true) {
				// System.out.println("while true");
				// QryIop q_left = (QryIop) this.args.get(0);
				// if (!q_left.locIteratorHasMatch()) {
				// break;
				// }
				//
				// int curLoc = q_left.locIteratorGetMatch();
				boolean moveToNextDoc = false;
				int curLoc = QryIopNear.INVALIDLOC;

				for (int i = 1; i < argsLen; i++) {
					QryIop q_left = (QryIop) this.args.get(i - 1);

					if (!q_left.locIteratorHasMatch()) {
						moveToNextDoc = true;
						break;
					}

					curLoc = q_left.locIteratorGetMatch();

					QryIop q_i = (QryIop) this.args.get(i);
					q_i.locIteratorAdvancePast(curLoc);

					// //The biggest location of the right file is smaller than
					// the location of the left file
					// In this case, should move to the next document
					if (!q_i.locIteratorHasMatch()) {
						// q_0.locIteratorAdvancePast(q_iLoc);
						curLoc = QryIopNear.INVALIDLOC;
						moveToNextDoc = true;
						break;
					}

					int q_iLoc = q_i.locIteratorGetMatch();

					// The distance is too far
					// In this case, just need to move the locIterator of the
					// first query
					if ((q_iLoc - curLoc) > distance) {
						q_left.locIteratorAdvance();
						curLoc = QryIopNear.INVALIDLOC;
						break;
					}

					curLoc = q_iLoc;
				}

				if (curLoc != QryIopNear.INVALIDLOC) {
					positions.add(curLoc);
					for (int i = 0; i < argsLen; i++) {
						QryIop q_i = (QryIop) this.args.get(i);
						q_i.locIteratorAdvance();
					}
				} else {
					if (moveToNextDoc) {
						break;
					}
				}
			}
			if (!positions.isEmpty()) {
				Collections.sort(positions);
				this.invertedList.appendPosting(docId_0, positions);
			}

			this.args.get(0).docIteratorAdvancePast(docId_0);
		}

	}

	// public List<Integer> locationMatch(int matchAllDocId, QryIop q_i_1,
	// QryIop q_i) {
	// List<Integer> positions = new ArrayList<Integer>();
	// while (true) {
	// if (q_i.docIteratorHasMatch(null)
	// && (q_i.docIteratorGetMatch() == matchAllDocId)
	// && q_i_1.docIteratorHasMatch(null)
	// && (q_i_1.docIteratorGetMatch() == matchAllDocId)) {
	// if (!q_i_1.locIteratorHasMatch()) {
	// break;
	// }
	//
	// int q_i_1Loc = q_i_1.locIteratorGetMatch();
	//
	// }
	// }
	//
	// return positions;
	//
	// }

	// @Override
	// protected boolean docIteratorHasMatchAll(RetrievalModel r) {
	// boolean matchFound = false;
	//
	// // Keep trying until a match is found or no match is possible.
	//
	// while (!matchFound) {
	//
	// // Get the docid of the first query argument.
	//
	// Qry q_1st = this.args.get(0);
	//
	// if (!q_1st.docIteratorHasMatch(r)) {
	// return false;
	// }
	//
	// int docid_0 = q_1st.docIteratorGetMatch();
	//
	// // Other query arguments must match the docid of the first query
	// // argument.
	//
	// matchFound = true;
	//
	// for (int i = 1; i < this.args.size(); i++) {
	// Qry q_i = this.args.get(i);
	//
	// q_i.docIteratorAdvanceTo(docid_0);
	//
	// if (!q_i.docIteratorHasMatch(r)) { // If any argument is
	// // exhausted
	// return false; // there are no more matches.
	// }
	//
	// int docid_i = q_i.docIteratorGetMatch();
	//
	// if (docid_0 != docid_i) { // docid_0 can't match. Try again.
	// q_1st.docIteratorAdvanceTo(docid_i);
	// matchFound = false;
	// break;
	// }
	// }
	//
	// if (matchFound) {
	// //docIteratorSetMatchCache(docid_0);
	// }
	// }
	//
	// return true;
	// }

}
