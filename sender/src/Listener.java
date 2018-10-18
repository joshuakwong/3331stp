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
//			boolean flag = false;
			try {
				incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
				socket.receive(incomingPacket);
				recvObj = STP.deserialize(incomingPayload);		
				recvSeqNum = ((STP) recvObj).getSeqNum();
				recvAckNum = ((STP) recvObj).getAckNum();
				
				int recvSeg = findSeg(recvAckNum);
				int firstUnackSeg = firstUnack();
				
				if (recvSeg == -1) {
					int old = Sender.firstSegment.getAckCount();
					Sender.firstSegment.setAckCount(old+1);
				}
//				normal mode
				else if (recvSeg == firstUnackSeg) {
					Sender.segments[recvSeg].setAckedFlag(true);
					Sender.segments[recvSeg].setAckCount(1);
				}
				
//				resend same ack to already acked packets
				else if (recvSeg < firstUnackSeg) {
					int old = Sender.segments[recvSeg].getAckCount();
					Sender.segments[recvSeg].setAckCount(old+1);
				}
				
				else if (recvSeg > firstUnackSeg) {
					for (int i=firstUnackSeg; i<=recvSeg; i++) {
						Sender.segments[i].setAckedFlag(true);
						Sender.segments[i].setAckCount(1);
					}
				}
				exit = checkAllAck();
				
				System.out.println("\nListener: received ack " + recvAckNum+"\t recvSeg: "+recvSeg+"\t firstUnacked: "+firstUnackSeg);
//				System.out.println("All ack flag state");
//				for (FilePacket item : Sender.segments) {
//					System.out.print(item.isAckedFlag()+"_");
//				}
//				System.out.println();
				
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
