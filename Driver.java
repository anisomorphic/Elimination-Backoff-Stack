// Michael Harris
// COP4520 - pa3
// Driver for EliminationBackoffStack.java

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class Driver<T> {
  public static final int THREADS = 1024;

  public static void main(String[] args) throws Exception {
    Thread threads[] = new Thread[THREADS];
    AtomicStack<Integer> ebs = new EliminationBackoffStack<>();

    long begin = System.currentTimeMillis();

    for (int i = 0; i < THREADS; i++) {
      threads[i] = new Thread(new AtomicDriver(ebs, i));
      threads[i].start();
    }

    for (int i = 0; i < THREADS; i++) {
        try {
          threads[i].join();
        } catch(Exception ex) {
          ;
      }
    }

    long end = System.currentTimeMillis();

    System.out.println("\nRuntime: " + (end - begin) + "ms.");
  }
}


class AtomicDriver implements Runnable {
  private final int id;
  public AtomicReference<AtomicStack<Integer>> stack;

  public AtomicDriver(AtomicStack<Integer> stack, int id) {
    this.stack = new AtomicReference<>(stack);
    this.id = id;
  }

  public void run() {
    int randomOp = (int)(Math.random() * 3);

    if (randomOp == 1) {
      int r = ThreadLocalRandom.current().nextInt(100, 10000);
      boolean push;

      try {
        push = stack.get().push(r);
      } catch(Exception ex) {
        ;
      }
    }
    else if (randomOp == 2){
      Integer pop;

      try {
        pop = stack.get().pop();
      } catch(Exception ex) {
        ;
      }
    }
    else {
      int size = stack.get().size();
    }
  }
}
