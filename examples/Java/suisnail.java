import java.util.Random;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZThread;
import org.zeromq.ZThread.IAttachedRunnable;

/**
 * Suicidal Snail
 *
 * @author Mariusz Ryndzionek <mryndzionek@gmail.com>
 *
 */

public class suisnail {

	public static long MAX_ALLOWED_DELAY = 1000;

	//  This is our subscriber. It connects to the publisher and subscribes
	//  to everything. It sleeps for a short time between messages to
	//  simulate doing too much work. If a message is more than one second
	//  late, it croaks
	private static class Subscriber implements IAttachedRunnable
	{

		@Override
		public void run(Object[] args, ZContext ctx, Socket pipe)
		{
			//  Subscribe to everything
			Socket subscriber = ctx.createSocket(ZMQ.SUB);
			subscriber.subscribe("".getBytes());
			subscriber.connect("tcp://localhost:5556");

			//  Get and process message
			while (!Thread.currentThread().isInterrupted()) {
				String string = subscriber.recvStr();
				System.out.println(string);

				//  Suicide snail logic
				if((System.currentTimeMillis() - Long.parseLong(string)) > MAX_ALLOWED_DELAY)
				{
					System.err.println("E: subscriber cannot keep up, aborting");
					break;
				}

				//  Work for 1 msec plus some random additional time
				Random rand = new Random(System.currentTimeMillis());
				try {
					Thread.sleep(1+rand.nextInt(2));
				} catch (InterruptedException e) {
				}

			}

			pipe.send("gone and died");
		}
	}

	//  This is our publisher task. It publishes a time-stamped message to its
	//  PUB socket every millisecond:
	private static class Publisher implements IAttachedRunnable
	{

		@Override
		public void run(Object[] args, ZContext ctx, Socket pipe)
		{
			//  Prepare publisher
			Socket publisher = ctx.createSocket(ZMQ.PUB);
			publisher.bind("tcp://*:5556");

			while (!Thread.currentThread().isInterrupted()) {
				//  Send current clock (msecs) to subscribers
				publisher.send(""+System.currentTimeMillis());

				String signal = pipe.recvStr(ZMQ.DONTWAIT);
				if(signal != null)
					break;

				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
				}
			}

		}
	}

	public static void main (String[] args) {
		//  The main task simply starts a client and a server, and then
		//  waits for the client to signal that it has died:
		ZContext ctx = new ZContext();
		Socket pubpipe = ZThread.fork(ctx, new Publisher());
		Socket subpipe = ZThread.fork(ctx, new Subscriber());

		subpipe.recvStr();
		pubpipe.send("break");

		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
		}

		ctx.destroy();
	}

}
