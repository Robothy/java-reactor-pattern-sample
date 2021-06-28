import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

class AcceptHandler implements Runnable {

    private Selector selector;

    private ServerSocketChannel serverSocketChannel;

    public AcceptHandler(Selector selector, ServerSocketChannel serverSocketChannel) {
        this.selector = selector;
        this.serverSocketChannel = serverSocketChannel;
    }

    @Override
    public void run() {
        try {
            SocketChannel socketChannel = this.serverSocketChannel.accept();
            socketChannel.configureBlocking(false);

            SelectionKey readEvent = socketChannel.register(selector, SelectionKey.OP_READ);
            readEvent.attach(new MessageHandler(socketChannel));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}