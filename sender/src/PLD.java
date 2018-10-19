import java.util.Random;

public class PLD {
	
	private double pDrop = 0;
	private double pDupl = 0;
	private double pCorr= 0;
	private double pOrder = 0;
	private double pDelay = 0;
	private Random rand = null;
	private float random = 0;
	
	

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
		random = rand.nextFloat();
		
		if (random < pDrop) {
			return "drop";
		}
		if (random < pDupl) {
			return "dupl";
		}
		if (random < pCorr) {
			corrupt(packet);
			return "corr";
		}
		if (random < pOrder) {
			return "reorder";
		}
		if (random < pDelay) {
			return "delay";
		}
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


	public double getpDrop() {
		return pDrop;
	}

	public void setpDrop(double pDrop) {
		this.pDrop = pDrop;
	}

	public double getpDupl() {
		return pDupl;
	}

	public void setpDupl(double pDupl) {
		this.pDupl = pDupl;
	}

	public double getpCorr() {
		return pCorr;
	}

	public void setpCorr(double pCorr) {
		this.pCorr = pCorr;
	}

	public double getpOrder() {
		return pOrder;
	}

	public void setpOrder(double pOrder) {
		this.pOrder = pOrder;
	}

	public double getpDelay() {
		return pDelay;
	}

	public void setpDelay(double pDelay) {
		this.pDelay = pDelay;
	}

	public Random getRand() {
		return rand;
	}

	public void setRand(Random rand) {
		this.rand = rand;
	}

	public float getRandom() {
		return random;
	}

	public void setRandom(float random) {
		this.random = random;
	}

	
}
