import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

public class PLD {
	
	private static double pDrop = 0;
	private static double pDupl = 0;
	private static double pCorr= 0;
	private static double pOrder = 0;
	private static double pDelay = 0;
	private static Random rand = null;
	private static float random = 0;
	
	PLD (double pDrop, double pDupl, double pCorr, double pOrder, double pDelay, int seed){
		this.pDrop = pDrop;
		this.pDupl = pDupl;
		this.pCorr = pCorr;
		this.pOrder = pOrder;
		this.pDelay = pDelay;
		this.rand = new Random(seed);
		this.random = rand.nextFloat();
	}
	
	
	public String action(STP packet) {
//		return "delay";
//		random = rand.nextFloat();
		random = 1; System.out.println();
		
		if (random < pDrop) {
			System.out.println("drop");
			return "drop";
		}
		else if (random < pDupl) {
			System.out.println("dupl");
			return "dupl";
		}
		else if (random < pCorr) {
			System.out.println("corr");
			corrupt(packet);
			return "send";
		}
		else if (random < pOrder) {
			System.out.println("reord");
			return "reorder";
		}
		else if (random < pDelay) {
			System.out.println("delay");
			return "delay";
		}
		System.out.println("send");
		return "send";
	}
	
	
	private void corrupt(STP packet) {
		byte chk = (byte) (packet.getData()[0] & 1);
		if (chk == (byte)1) {
			packet.getData()[0] = (byte) (packet.getData()[0] & ~((byte) 1));
		}
		else {
			packet.getData()[0] = (byte) (packet.getData()[0] | ((byte) 1));
		}
	}


	public static double getpDrop() {
		return pDrop;
	}

	public static void setpDrop(double pDrop) {
		PLD.pDrop = pDrop;
	}

	public static double getpDupl() {
		return pDupl;
	}

	public static void setpDupl(double pDupl) {
		PLD.pDupl = pDupl;
	}

	public static double getpCorr() {
		return pCorr;
	}

	public static void setpCorr(double pCorr) {
		PLD.pCorr = pCorr;
	}

	public static double getpOrder() {
		return pOrder;
	}

	public static void setpOrder(double pOrder) {
		PLD.pOrder = pOrder;
	}

	public static float getRandom() {
		return random;
	}

	public static void setRandom(float random) {
		PLD.random = random;
	}
	
	
}
