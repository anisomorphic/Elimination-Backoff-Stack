// Michael Harris
// COP4520 - pa3
// Modified AtomicStack to include the elimination and backoff array from Ch. 11, code used from text where cited

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;



// Art of Multiprocessor Programming, pg 250
class LockFreeExchanger<T> {
  static final int EMPTY = 0;
  static final int WAITING = 1;
  static final int BUSY = 2;

  AtomicStampedReference<T> slot;

  public LockFreeExchanger() {
    this.slot = new AtomicStampedReference<T>(null, 0);
  }

  public T exchange(T myItem, long timeout, TimeUnit unit) throws TimeoutException {
    long nanos = unit.toNanos(timeout);
    long timeBound = System.nanoTime() + nanos;

    int[] stampHolder = {EMPTY};

    while (true) {
      if (System.nanoTime() > timeBound)
        throw new TimeoutException();

      T yrItem = slot.get(stampHolder);
      int stamp = stampHolder[0];

      switch (stamp) {
        case EMPTY:
          if (slot.compareAndSet(yrItem, myItem, EMPTY, WAITING)) {
            while (System.nanoTime() < timeBound) {
              yrItem = slot.get(stampHolder);

              if (stampHolder[0] == BUSY) {
                slot.set(null, EMPTY);
                return yrItem;
              }
            }
            if (slot.compareAndSet(myItem, null, WAITING, EMPTY)) {
              throw new TimeoutException();
            }
            else {
              yrItem = slot.get(stampHolder);
              slot.set(null, EMPTY);
              return yrItem;
            }
          }
          break;
        case WAITING:
          if (slot.compareAndSet(yrItem, myItem, WAITING, BUSY))
            return yrItem;
          break;
        case BUSY:
          break;
        default: //impossible
          break;
      }
    }
  }
}


// Art of Multiprocessor Programming, pg 252
class EliminationArray<T> {
  private static final int duration = 1;
  ArrayList<LockFreeExchanger<T>> exchanger;
  Random random;


  public EliminationArray(int capacity) {
    this.exchanger = new ArrayList<LockFreeExchanger<T>>(capacity);

    for (int i = 0; i < capacity; i++)
      exchanger.add(new LockFreeExchanger<T>());

    this.random = new Random();
  }


  public T visit(T value, int range) throws TimeoutException {
    int slot = random.nextInt(range);
    return exchanger.get(slot).exchange(value, duration, TimeUnit.MILLISECONDS);
  }
}



// Art of Multiprocessor Programming, pg 253
public class EliminationBackoffStack<T> extends AtomicStack<T> {
  static final int capacity = 100000;

  EliminationArray<T> eliminationArray;
  AtomicInteger size;
  static ThreadLocal<RangePolicy> policy;

  public EliminationBackoffStack() {
    this.eliminationArray = new EliminationArray<T>(capacity);
    this.size = new AtomicInteger(0);
    this.policy = new ThreadLocal<RangePolicy>() {
      protected synchronized RangePolicy initialValue() {
        return new RangePolicy(capacity);
      }
    };
  }


  protected boolean tryPush(Node<T> node) {
    Node<T> currHead = head.get();
    node.next = currHead;

    return head.compareAndSet(currHead, node);
  }


  public boolean push(T x) {
    RangePolicy rangePolicy = policy.get();

    Node<T> node = new Node<T>(null);
    node.setVal(x);

    while (true) {
      if (tryPush(node)) {
        return true;
      }
      else try {
        T otherValue = eliminationArray.visit(x, rangePolicy.getRange());

        if (otherValue == null) {
          rangePolicy.recordEliminationSuccess();

          return true;
        }
      } catch (TimeoutException ex) {
        rangePolicy.recordEliminationTimeout();
      }
    }
  }


  protected Node<T> tryPop() throws EmptyStackException {
    Node<T> currHead = head.get();

    if (currHead == null)
      throw new EmptyStackException();

    Node<T> newHead = currHead.next;

    if (head.compareAndSet(currHead, newHead))
      return currHead;

    return null;
  }


  public T pop() throws EmptyStackException {
    RangePolicy rangePolicy = policy.get();

    while (true) {
      Node<T> returnNode = tryPop();

      if (returnNode != null) {

        return returnNode.val;
      } else try {
        T otherValue = eliminationArray.visit(null, rangePolicy.getRange());

        if (otherValue != null) {
          rangePolicy.recordEliminationSuccess();

          return otherValue;
        }
      } catch(TimeoutException ex) {
        rangePolicy.recordEliminationTimeout();
      }
    }
  }
}



// thread local objects that determine the subrange to be used in the EliminationArray
class RangePolicy {
  int max;
  int range = 1;

  public RangePolicy(int max) {
    this.max = max;
  }

  public void recordEliminationSuccess() {
    if (max > range) {
      range++;
    }
  }

  public void recordEliminationTimeout() {
    if (range > 1) {
      range--;
    }
  }

  public int getRange() {
    return range;
  }
}



// methods from previous assignments
// node class
class Node<T> {
  public T val;
  public Node<T> next;

  // custom node constructor, sets node value
  public Node(T _val) {
    this.val = _val;
    this.next = null;
  }

  // accessor/setters
  public T getVal() {
      return val;
  }

  public void setVal(T e) {
      val = e;
  }

  public Node<T> getNext() {
      return next;
  }
}

// descriptor class
class Descriptor<T> {
  AtomicInteger size;
  WriteDescriptor<T> wd;

  public Descriptor(int size, WriteDescriptor<T> wd) {
    this.size = new AtomicInteger(size);
    this.wd = wd;
  }

  // accessor
  public int getSize() {
    return size.get();
  }
}

// write descriptor
class WriteDescriptor<T> {
  AtomicBoolean pending;
  Node<T> old_val;
  Node<T> new_val;

  public WriteDescriptor(Node<T> old_val, Node<T> new_val) {
    this.pending = new AtomicBoolean(true);
    this.old_val = old_val;
    this.new_val = new_val;
  }

  // accessor and setter
  public boolean getPend() {
    return pending.get();
  }

  public void setPending(boolean b) {
    pending.set(b);
  }
}


// atomic stack, includes head, numOps (from hw1, moved to complete_write), and a descriptor)
class AtomicStack<T> {
  AtomicReference<Node<T>> head;
  AtomicInteger numOps;
  AtomicReference<Descriptor<T>> desc;

  public AtomicStack() {
    head = new AtomicReference<>();
    numOps = new AtomicInteger(0);
    desc = new AtomicReference<>(new Descriptor<T>(0, null));
  }


  // push function
  // credit: Damian Dechev, Peter Pirkelbauer, Bjarne Stroustrup
  public boolean push(T e) {
    Node<T> currHead;
    Descriptor<T> newDesc;
    Descriptor<T> currDesc;
    WriteDescriptor<T> toWrite;

    // create the new node to push that will become the new head
    Node<T> newNode = new Node<T>(null);
    newNode.setVal(e);

    // repeat until descriptor objects are able to swap, otherwise reinit and try again
    do {
      // complete pending
      currDesc = desc.get();
      complete_write(currDesc.wd);

      // set up new head
      currHead = head.get();
      newNode.next = currHead;

      // this does the actual swap
      toWrite = new WriteDescriptor<>(currHead, newNode);

      // build new descriptor with above write op
      newDesc = new Descriptor<>(currDesc.size.get() + 1, toWrite);

    } while (!desc.compareAndSet(currDesc, newDesc));

    // complete this write and return true
    complete_write(newDesc.wd);
    return true;
  }


  // pop from stack, inverse of above function
  public T pop() {
    Node<T> newHead;
    Node<T> oldHead;
    Descriptor<T> newDesc;
    Descriptor<T> currDesc;
    WriteDescriptor<T> toWrite;

    // another do-while to reinit and try until CAS succeeds in swapping descriptors
    do {
      // complete pending
      currDesc = desc.get();
      complete_write(currDesc.wd);

      // store old head, val to be popped
      oldHead = head.get();

      // handle empty stack
      if (oldHead == null)
        return null;

      // set up new head
      newHead = oldHead.next;

      // indicate the swap op with the write descriptor
      toWrite = new WriteDescriptor<>(oldHead, newHead);

      // build new descriptor with above write op
      newDesc = new Descriptor<>(currDesc.size.get() - 1, toWrite);

    } while (!desc.compareAndSet(currDesc, newDesc));

    // complete this op and return popped value
    complete_write(newDesc.wd);
    return oldHead.getVal();
  }


  // helper function to do the actual CAS from hw1
  public void complete_write(WriteDescriptor<T> w) {
    // nothing pending, already done
    if (w == null)
      return;

    // here's where the action happens
    if (w.getPend()) {
      // cas the head with the new head if we see old head
      head.compareAndSet(w.old_val, w.new_val);

      // set flag to done, increment numOps
      w.setPending(false);
      numOps.getAndIncrement();
    }
  }


  // size of atomic stack, descriptor has correct size count based on pending op
  public int size() {
    Descriptor<T> temp = desc.get();

    return temp.size.get();
  }



  // from hw1
  public int getNumOps() {
    return numOps.get();
  }
}
