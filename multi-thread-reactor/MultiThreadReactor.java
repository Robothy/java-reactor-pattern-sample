import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MultiThreadReactor {

    public static void main(String[] args) throws IOException {
        new MultiThreadReactor(5, 5, 9090).start();
    }

    private int workerNum;

    private int reactorNum;

    private int port;

    public MultiThreadReactor(int workerNum, int reactorNum, int port) {
        this.workerNum = workerNum;
        this.reactorNum = reactorNum;
        this.port = port;
    }

    /**
     * startup reactorNum reactors;
     * create executor service with workNum threads.
     */
    public void start() throws IOException {
        Selector[] selectors = new Selector[this.reactorNum];
        for (int i = 0; i < this.reactorNum; i++) {
            selectors[i] = Selector.open();
        }

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(this.port));

        SelectionKey acceptEvent = serverSocketChannel.register(selectors[0], SelectionKey.OP_ACCEPT);
        acceptEvent.attach(new Acceptor(serverSocketChannel, selectors));

        ExecutorService executorService = Executors.newFixedThreadPool(this.reactorNum);

        for (int i = 0; i < workerNum; i++) {
            SubReactor reactor = new SubReactor(executorService, selectors[i]);
            new Thread(() -> {
                try {
                    reactor.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        }

        System.out.println("Server Started.");
    }
}

/**
 * SubReactor
 * Each SubReactor with single selector.
 */
class SubReactor {

    private ExecutorService executorService;

    private Selector selector;

    public SubReactor(ExecutorService executorService, Selector selector) {
        this.executorService = executorService;
        this.selector = selector;
    }

    public void start() throws IOException {
        while (!Thread.currentThread().isInterrupted()) {
            selector.select();
            Set<SelectionKey> events = selector.selectedKeys();
            for (SelectionKey event : events) {
                System.out.println(Thread.currentThread().getName() + " -> " + (event.isReadable() ? "Read" : "Accept"));
                dispatch(event);
            }
            events.clear();
        }
    }

    void dispatch(SelectionKey event) {
        int ops = event.interestOps();
        // Clear interest operations to avoid repeatedly select events that are being process by workers.
        // This may cause the reactor blocking. So another thread need to wakeup the selector after reset the interest operations.
        event.interestOps(0);
        this.executorService.execute(new AsyncTask( (Runnable) event.attachment(), event,  ops));
    }
}

/**
 * accept connections register the interest event.
 */
class Acceptor implements Runnable {

    private Selector[] selectors;

    private AtomicInteger nextSelector;

    private ServerSocketChannel serverSocketChannel;


    public Acceptor(ServerSocketChannel serverSocketChannel, Selector[] selectors) {
        this.serverSocketChannel = serverSocketChannel;
        this.selectors = selectors;
        this.nextSelector = new AtomicInteger();
    }

    @Override
    public void run() {
        try {
            SocketChannel socketChannel = this.serverSocketChannel.accept();
            socketChannel.configureBlocking(false);
            SelectionKey readEvent = socketChannel.register(this.selectors[nextSelector.getAndIncrement()], SelectionKey.OP_READ);
            readEvent.attach(new MessageHandler(socketChannel));
            readEvent.selector().wakeup();
            if (nextSelector.get() == selectors.length) {
                nextSelector.set(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

/**
 * Invoke handlers and reset interest operations.
 */
class AsyncTask implements Runnable {

    private Runnable handler;

    private SelectionKey selectionKey;

    private int ops;

    public AsyncTask(Runnable handler, SelectionKey selectionKey, int interestOps) {
        this.handler = handler;
        this.selectionKey = selectionKey;
        this.ops = interestOps;
    }

    @Override
    public void run() {
        handler.run();
        if(this.selectionKey.isValid()){
            this.selectionKey.interestOps(ops);

            // Need wakeup the selector after change the interest operations.
            this.selectionKey.selector().wakeup();
        }
    }
}