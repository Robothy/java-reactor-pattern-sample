import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

class MessageHandler implements Runnable {

    private SocketChannel socketChannel;

    private ByteBuffer buf;

    static final String ACK = "Message Received.";

    public MessageHandler(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
        this.buf = ByteBuffer.allocate(1024);
    }

    @Override
    public void run() {
        try {
            StringBuilder msg = new StringBuilder();
            int size;
            while ((size = this.socketChannel.read(buf)) > 0) {
                buf.flip();
                while (buf.position() < buf.limit()) {
                    msg.append((char) buf.get());
                }
                buf.clear();
            }

            if (size == -1) {
                System.out.println(this.socketChannel + " Exited.");
                this.socketChannel.close();
            } else {
                System.out.println(msg);
                send(ACK);
            }
        } catch (IOException e) {
            e.printStackTrace();
            try {
                this.socketChannel.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }

    }

    private void send(String msg) throws IOException {
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            buf.put(b);
            if (buf.position() == buf.capacity() - 1) {
                buf.flip();
                this.socketChannel.write(buf);
                buf.compact();
            }
        }

        buf.flip();
        while (buf.hasRemaining()) {
            this.socketChannel.write(buf);
        }

        buf.clear();
    }
}