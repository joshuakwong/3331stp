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
			try {
				incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
				socket.receive(incomingPacket);
				recvObj = STP.deserialize(incomingPayload);
				
				recvSeqNum = ((STP) recvObj).getSeqNum();
				recvAckNum = ((STP) recvObj).getAckNum();
				
				for (int i=0; i<Sender.segments.length; i++) {
					if (recvAckNum == Sender.segments[i].getExpAck()) {
						Sender.segments[i].setAckedFlag(true);
					}
				}
				exit = checkAllAck();
				System.out.println("Listener: received ack " + recvAckNum);
			} catch (IOException | ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
		Sender.currAck = recvSeqNum;
		Sender.currSeq = recvAckNum;
		System.out.println("All ackedFlag state: ");
		for (int i=0; i<Sender.segments.length; i++)
			System.out.print(Sender.segments[i].isAckedFlag()+"__");
		System.out.println("\n-----------------ending listener-----------------, exit = "+exit);
	}

	public static boolean checkAllAck() {
		int i=0;
		for (i=0; i<Sender.segments.length; i++) 
			if (Sender.segments[i].isAckedFlag() == false) return false;
		
//		System.out.println(i);
		return true;
	}
	
	public static void setExit(boolean exit) {
		Listener.exit = exit;
	}
	
}
