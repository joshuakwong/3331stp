import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.zip.CRC32;


public class STP implements Serializable{
	

	private long checksum;
	private Boolean synFlag;
	private Boolean ackFlag;
	private Boolean finFlag;
	private int seqNum;
	private int ackNum;
	private byte[] data;
	private int mss;
	
	
	public STP(Boolean synFlag, Boolean ackFlag, Boolean finFlag, int seqNum, int ackNum){
		this.checksum = 0;
		this.synFlag = synFlag;
		this.ackFlag = ackFlag;
		this.finFlag = finFlag;
		this.seqNum = seqNum;
		this.ackNum = ackNum;
	}
	
	public STP(Boolean synFlag, Boolean ackFlag, Boolean finFlag, int seqNum, int ackNum, int mss){
		this.checksum = 0;
		this.synFlag = synFlag;
		this.ackFlag = ackFlag;
		this.finFlag = finFlag;
		this.seqNum = seqNum;
		this.ackNum = ackNum;		
		this.setMss(mss);
	}
	
	public STP( Boolean synFlag, Boolean ackFlag, Boolean finFlag, int seqNum, int ackNum, byte[] data, int mss){
		this.checksum  = genChecksum(data);
		this.synFlag = synFlag;
		this.ackFlag = ackFlag;
		this.finFlag = finFlag;
		this.seqNum = seqNum;
		this.ackNum = ackNum;	
		this.data = data;
		this.setMss(mss);
	}
	
	public byte[] serialize () {
		byte[] payload = null;
		ByteArrayOutputStream bOutput = new ByteArrayOutputStream();
		try {
			ObjectOutput objOut = new ObjectOutputStream(bOutput);
			objOut.writeObject(this);
			objOut.flush();
			payload = bOutput.toByteArray();
		} catch (IOException e) {
		}
		return payload;
	}
	
	public static Object deserialize(byte[] recv) throws IOException, ClassNotFoundException {
		ByteArrayInputStream bInput = new ByteArrayInputStream(recv);
		ObjectInputStream oIn = new ObjectInputStream(bInput);
		return oIn.readObject();
	}
	
	private long genChecksum(byte[] data) {
		long res = 0;
		CRC32 checksum = new CRC32();
		checksum.update(data);
		res = checksum.getValue();
		
		return res;
	}
	
	public long getChecksum() {
		return checksum;
	}

	public Boolean isSynFlag() {
		return synFlag;
	}

	public Boolean isAckFlag() {
		return ackFlag;
	}

	public Boolean isFinFlag() {
		return finFlag;
	}

	public int getSeqNum() {
		return seqNum;
	}

	public int getAckNum() {
		return ackNum;
	}

	public byte[] getData() {
		return data;
	}

	public int getMss() {
		return mss;
	}

	public void setMss(int mss) {
		this.mss = mss;
	}
	
	
	
}













