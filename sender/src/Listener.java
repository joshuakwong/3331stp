import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class Listener implements Runnable{

	private final int MAXSIZE = 1024;
	private DatagramSocket socket = null;
	private DatagramPacket incomingPacket = null;
	private byte[] incomingPayload = new byte[5000];
	private Object recvObj = null;
	private boolean exit = false;
	
	Listener(DatagramSocket socket){
		this.socket = socket;
	}
	
	
	@Override
	public void run(){
		System.out.println("-----------------starting listener-----------------");
		
		int recvSeqNum = 0;
		int recvAckNum = 0;
		while (exit == false) {
			try {
				incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
				socket.receive(incomingPacket);
				recvObj = STP.deserialize(incomingPayload);		
				recvSeqNum = ((STP) recvObj).getSeqNum();
				recvAckNum = ((STP) recvObj).getAckNum();
				
				int recvSeg = findSeg(recvAckNum);
				int firstUnackSeg = firstUnack();
				
				if (recvSeg == -1) {
					System.out.println("first seg:"+Sender.firstSegment.isAckedFlag());
					if (Sender.firstSegment.isAckedFlag() == false) {
						Sender.logger("rcv", "A",  recvSeqNum, 0, recvAckNum);
						Sender.firstSegment.setAckedFlag(true);
						Sender.firstSegment.setAckCount(0);
						Sender.firstSegment.setEndTime(System.currentTimeMillis());
					}
					
					else {
						Sender.logger("rcv/DA", "A",  recvSeqNum, 0, recvAckNum);
						int old = Sender.firstSegment.getAckCount();
						Sender.firstSegment.setAckCount(old+1);
						Sender.countDuplAck++;
					}
				}

//				first ack received for a segment
				else if (recvSeg == firstUnackSeg) {
					Sender.logger("rcv", "A",  recvSeqNum, 0, recvAckNum);
					Sender.segments[recvSeg].setAckedFlag(true);
					Sender.segments[recvSeg].setAckCount(0);
					Sender.segments[recvSeg].setEndTime(System.currentTimeMillis());
				}
				
//				receiving same ack for a segment 
				else if (recvSeg < firstUnackSeg) {
					Sender.logger("rcv/DA", "A",  recvSeqNum, 0, recvAckNum);
					int old = Sender.segments[recvSeg].getAckCount();
					Sender.segments[recvSeg].setAckCount(old+1);
					Sender.countDuplAck++;
				}
				
//				ack assumption
				else if (recvSeg > firstUnackSeg) {
					Sender.logger("rcv", "A",  recvSeqNum, 0, recvAckNum);
					for (int i=firstUnackSeg; i<=recvSeg; i++) {
						Sender.segments[i].setAckedFlag(true);
						Sender.segments[i].setAckCount(0);
						Sender.segments[i].setEndTime(System.currentTimeMillis());
					}
				}
				exit = checkAllAck();
				
				System.out.println("\nListener: received ack " + recvAckNum+"   recvSeg: "+recvSeg
						+"   firstUnacked: "+firstUnack());
				
//				System.out.println("reacked flag states");
//				for (FilePacket seg : Sender.segments) {
//					System.out.print(seg.getAckCount()+"_ ");
//				}
			} catch (IOException | ClassNotFoundException e) {
				e.printStackTrace();
			}
			System.out.println();
		}
		Sender.currAck = recvSeqNum;
		Sender.currSeq = recvAckNum;
		System.out.println("\n-----------------ending listener-----------------");
	}
	
	public int findSeg(int recvAck) {
		for (int i=0; i<Sender.segments.length; i++) 
			if (Sender.segments[i].getExpAck() == recvAck) return i;
		
		return -1;
	}
	
	public int firstUnack() {
		for (int i=0; i<Sender.segments.length; i++) 
			if (Sender.segments[i].isAckedFlag() == false) return i;
		
		return -1;
	}
	

	public boolean checkAllAck() {
		for (int i=0; i<Sender.segments.length; i++) 
			if (Sender.segments[i].isAckedFlag() == false) return false;
		
		return true;
	}
	
	public void setExit(boolean exit) {
		this.exit = exit;
	}
	
}
