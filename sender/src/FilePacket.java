
public class FilePacket {
	
	private byte[] data;
	private int startNum;
	private int endNum;
	private boolean sentFlag;
	private boolean ackedFlag;
	private int expAck;
	
	FilePacket(byte[] data, int startNum, int endNum) {
		this.data = data;
		this.startNum = startNum;
		this.endNum = endNum;
		this.sentFlag = false;
		this.ackedFlag = false;
		this.expAck = -1;
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}

	public int getStartNum() {
		return startNum;
	}

	public void setStartNum(int startNum) {
		this.startNum = startNum;
	}

	public int getEndNum() {
		return endNum;
	}

	public void setEndNum(int endNum) {
		this.endNum = endNum;
	}

	public boolean isSentFlag() {
		return sentFlag;
	}

	public void setSentFlag(boolean sentFlag) {
		this.sentFlag = sentFlag;
	}

	public boolean isAckedFlag() {
		return ackedFlag;
	}

	public void setAckedFlag(boolean ackedFlag) {
		this.ackedFlag = ackedFlag;
	}

	public int getExpAck() {
		return expAck;
	}

	public void setExpAck(int expAck) {
		this.expAck = expAck;
	}

}
