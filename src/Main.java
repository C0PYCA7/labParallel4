import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.PriorityBlockingQueue;

class RingBuffer {
    private final String[] buffer;
    private int writeIndex;
    private int readIndex;
    private int messageCount;
    private final Lock lock;
    private final Condition notFull;
    private final Condition notEmpty;
    private final PriorityBlockingQueue<WriterTask> writersQueue;
    private final PriorityBlockingQueue<ReaderTask> readersQueue;
    private boolean finished;

    public RingBuffer(int capacity)  {//конструктор
        this.buffer = new String[capacity];
        this.writeIndex = 0;
        this.readIndex = 0;
        this.messageCount = 0;
        this.lock = new ReentrantLock();
        this.notFull = lock.newCondition();
        this.notEmpty = lock.newCondition();
        this.writersQueue = new PriorityBlockingQueue<>();
        this.readersQueue = new PriorityBlockingQueue<>();
        this.finished = false;
    }

    public void write(String message, int priority) {// метод записи в буфер
        lock.lock();
        try {
            while (messageCount == buffer.length) {
                notFull.await();
            }

            WriterTask writerTask = new WriterTask(message, priority);
            writersQueue.offer(writerTask);
            notEmpty.signal();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }

    public String read(int priority) {//метод чтения из буфера
        lock.lock();
        try {
            while (messageCount == 0 && !finished) {
                notEmpty.await();
            }

            ReaderTask readerTask = new ReaderTask(priority);
            readersQueue.offer(readerTask);

            notFull.signal();
            while (!finished) {
                WriterTask topWriter = writersQueue.peek();
                ReaderTask topReader = readersQueue.peek();

                if (topWriter == null || (topReader != null && topReader.priority > topWriter.priority)) {
                    readersQueue.poll();
                    if (messageCount == 0) {
                        notFull.signal();
                    } else {
                        String message = buffer[readIndex] != null ? buffer[readIndex] : "";
                        buffer[readIndex] = null;
                        readIndex = (readIndex + 1) % buffer.length;
                        messageCount--;
                        return message;
                    }
                } else {
                    notFull.signal();
                    notEmpty.await();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
        return "";
    }

    public void signalAllReaders() {// Метод для уведомления всех читателей о завершении работы.
        lock.lock();
        try {
            finished = true;
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }
}

class WriterTask implements Comparable<WriterTask> {
    public String message;
    public int priority;

    public WriterTask(String message, int priority) {
        this.message = message;
        this.priority = priority;
    }

    @Override
    public int compareTo(WriterTask other) { // сравнение по приоритету
        return other.priority - this.priority;
    }
}

class ReaderTask implements Comparable<ReaderTask> {
    public int priority;

    public ReaderTask(int priority) {
        this.priority = priority;
    }

    @Override
    public int compareTo(ReaderTask other) { // сравнение по приоритету
        return other.priority - this.priority;
    }
}

public class Main {
    public static void main(String[] args) {
        RingBuffer buffer = new RingBuffer(10);

        Thread[] writers = new Thread[6];
        for (int i = 0; i < writers.length; i++) {
            final int writerIndex = i;
            writers[i] = new Thread(() -> {
                int countOfMessages = 20;
                long startTime = System.currentTimeMillis();
                for (int j = 1; j <= countOfMessages; j++) {
                    String message = "Message from Writer " + writerIndex + " - " + j;
                    int priority = writerIndex;
                    buffer.write(message, priority);
                }
                long endTime = System.currentTimeMillis();
                long elapsedTime = endTime - startTime;
                System.out.println("Writer " + writerIndex + " worked " + elapsedTime + " ms");
                System.out.println("Writer " + writerIndex + " wrote " + countOfMessages + " messages");
            });
            writers[i].start();
        }

        Thread[] readers = new Thread[2];
        for (int i = 0; i < readers.length; i++) {
            final int readerIndex = i;
            readers[i] = new Thread(() -> {
                long startTime = System.currentTimeMillis();
                for (int j = 1; j <= 60; j++) {
                    int priority = readerIndex;
                    String message = buffer.read(priority);
                    if (message.isEmpty()) {
                        break;
                    }
                    System.out.println("Reader " + readerIndex + " read: " + message);
                }
                long endTime = System.currentTimeMillis();
                long elapsedTime = endTime - startTime;
                System.out.println("Reader " + readerIndex + " worked " + elapsedTime + " ms");
            });
            readers[i].start();
        }

        for (Thread writer : writers) {
            try {
                writer.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        buffer.signalAllReaders();

        for (Thread reader : readers) {
            try {
                reader.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
