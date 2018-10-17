import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class Listener implements Runnable{

	private static final int MAXSIZE = 1024;
	private static DatagramSocket socket = null;
	private static DatagramPacket incomingPacket = null;
	private static byte[] incomingPayload = new byte[5000];
	private static Object recvObj = null;
	private static boolean exit = false;
	
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
				
//				special case for first packet
				for (int i=0; i<Sender.segments.length; i++) {
					if (recvAckNum == Sender.segments[i].getExpAck()) {
//						flag = true;
						Sender.segments[i].setAckedFlag(true);
//						System.out.println("segment "+i+" has been set to true, with expackNum = "+recvAckNum);
					}
				}
//				System.out.println("-----------------All acked state-----------------");
//				for (int i=0; i<Sender.segments.length; i++) {
//					System.out.print(Sender.segments[i].isAckedFlag()+"_");
//				}
				exit = checkAllAck();
//				if (flag == false) System.out.println("\nListener: received ack " + recvAckNum+"  things never got changed");
//				if (flag == true) System.out.println("\nListener: received ack " + recvAckNum+"  HOLY FUCKKKKK");
				System.out.print("\nListener: received ack " + recvAckNum);
			} catch (IOException | ClassNotFoundException e) {
				e.printStackTrace();
			}
			System.out.println();
		}
		Sender.currAck = recvSeqNum;
		Sender.currSeq = recvAckNum;
//		System.out.println("All ackedFlag state: ");
//		for (int i=0; i<Sender.segments.length; i++)
//			System.out.print(Sender.segments[i].isAckedFlag()+"__");
		System.out.println("\n-----------------ending listener-----------------");
	}

	public static boolean checkAllAck() {
		int i=0;
		for (i=0; i<Sender.segments.length; i++) 
			if (Sender.segments[i].isAckedFlag() == false) return false;
		
		return true;
	}
	
	public static void setExit(boolean exit) {
		Listener.exit = exit;
	}
	
}
