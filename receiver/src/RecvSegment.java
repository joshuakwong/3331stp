
public class RecvSegment implements Comparable<RecvSegment>{

	private int recvSeq;
	private int recvAck;
	private byte[] data;
	private int length;
	
	RecvSegment(int recvSeq, int recvAck, int length, byte[] data){
		this.recvSeq = recvSeq;
		this.recvAck = recvAck;
		this.length = length;
		this.data = data;
	}
	
	@Override
	public int compareTo(RecvSegment o) {
		return this.getRecvSeq() - o.getRecvSeq();
//		return 0;
	}
	
	
	public int getRecvSeq() {
		return recvSeq;
	}

	public void setRecvSeq(int recvSeq) {
		this.recvSeq = recvSeq;
	}

	public int getRecvAck() {
		return recvAck;
	}

	public void setRecvAck(int recvAck) {
		this.recvAck = recvAck;
	}

	public int getLength() {
		return length;
	}

	public void setLength(int length) {
		this.length = length;
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}





	
}
