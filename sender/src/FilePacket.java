
public class FilePacket {
	
	private byte[] data;
	private int startNum;
	private int endNum;
	private boolean sentFlag;
	private boolean ackedFlag;
	private boolean resendFlag;
	private int expAck;
	private int ackCount;
	private long startTime;
	private long endTime;
	
	FilePacket(byte[] data, int startNum, int endNum) {
		this.data = data;
		this.startNum = startNum;
		this.endNum = endNum;
		this.sentFlag = false;
		this.ackedFlag = false;
		this.setResendFlag(false);
		this.expAck = -1;
		this.ackCount = -1;
		this.setStartTime(0);
		this.endTime = 0;
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

	public long getStartTime() {
		return startTime;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public int getAckCount() {
		return ackCount;
	}

	public void setAckCount(int ackCount) {
		this.ackCount = ackCount;
	}

	public long getEndTime() {
		return endTime;
	}

	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}

	public boolean isResendFlag() {
		return resendFlag;
	}

	public void setResendFlag(boolean resendFlag) {
		this.resendFlag = resendFlag;
	}

}
