import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

public class SingleThreadReactor {

    public static void main(String[] args) throws IOException {
        new SingleThreadReactor(9090).start();
    }

    private Selector selector;

    private ServerSocketChannel serverSocketChannel;

    public SingleThreadReactor(int port) throws IOException {
        this.selector = Selector.open();
        this.serverSocketChannel = ServerSocketChannel.open();
        this.serverSocketChannel.bind(new InetSocketAddress(port));
        this.serverSocketChannel.configureBlocking(false);
        SelectionKey acceptEvent = this.serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        acceptEvent.attach(new AcceptHandler(selector, serverSocketChannel));
    }

    void start() {
        new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    selector.select();
                    for (SelectionKey event : selector.selectedKeys()) {
                        this.dispatch(event);
                    }
                    selector.selectedKeys().clear();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        System.out.println("Server started.");
    }

    void dispatch(SelectionKey selectionKey) {
        Runnable handler = (Runnable) selectionKey.attachment();
        handler.run();
    }
}



