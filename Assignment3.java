import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicMarkableReference;

public class Assignment3 {
	public static void main(String... args) {
		// comment one out to run only the other
		minotaurBirthday();
		atmosphericTemperatureReading();
	}

	public static final int MAX_TEMP = 70;
	public static final int MIN_TEMP = -100;

	public static void atmosphericTemperatureReading() {
		var temperatureProbes = new ArrayList<Thread>();
		var random = new Random();
		var lowest = new AtomicIntegerArray(
				new int[] { MAX_TEMP + 1, MAX_TEMP + 1, MAX_TEMP + 1, MAX_TEMP + 1, MAX_TEMP + 1 });
		var highest = new AtomicIntegerArray(
				new int[] { MIN_TEMP - 1, MIN_TEMP - 1, MIN_TEMP - 1, MIN_TEMP - 1, MIN_TEMP - 1 });
		var deltas = new AtomicIntegerArray(new int[] { 0, 0, 0, 0, 0 });
		for (int i = 0; i < 8; i++) {
			var probe = new Thread(() -> {
				int last = 0;
				for (int minute = 0; minute < 60; minute++) {
					int reading = random.nextInt(MIN_TEMP, MAX_TEMP + 1);
					int delta = Math.abs(reading - last);
					// update lowest
					if (reading < lowest.get(lowest.length() - 1)) {
						int j;
						for (j = lowest.length() - 2; j >= 0; j--) {
							if (reading > lowest.get(j)) {
								j++;
								break;
							}
						}
						j = Math.max(j, 0);
						while (j < lowest.length()) {
							int val = lowest.get(j);
							if (reading < val) {
								if (lowest.compareAndSet(j, val, reading)) {
									break;
								}
							} else {
								j++;
							}
						}
					}
					// update highest
					if (reading > highest.get(0)) {
						int j;
						for (j = 1; j < highest.length(); j++) {
							if (reading < highest.get(j)) {
								j--;
								break;
							}
						}
						j = Math.min(j, highest.length() - 1);
						while (j >= 0) {
							int val = highest.get(j);
							if (reading > val) {
								if (highest.compareAndSet(j, val, reading)) {
									break;
								}
							} else {
								j--;
							}
						}
					}
					// update delta
					if (minute != 0 && delta > deltas.get(0)) {
						int j;
						for (j = 1; j < deltas.length(); j++) {
							if (delta < deltas.get(j)) {
								j--;
								break;
							}
						}
						j = Math.min(j, deltas.length() - 1);
						while (j >= 0) {
							int val = deltas.get(j);
							if (delta > val) {
								if (deltas.compareAndSet(j, val, delta)) {
									break;
								}
							} else {
								j--;
							}
						}
					}
					last = reading;
				}
			});
			temperatureProbes.add(probe);
			probe.start();
		}

		for (var probe : temperatureProbes) {
			try {
				probe.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.print("Lowest recorded temps: ");
		System.out.println(lowest.toString());
		System.out.print("Highest recorded temps: ");
		System.out.println(highest.toString());
		System.out.print("Biggest deltas: ");
		System.out.println(deltas.toString());
	}

	public static void minotaurBirthday() {
		// prepare the presents
		var presents = new ArrayList<Integer>();
		for (int i = 0; i < 500000; i++) {
			presents.add(i);
		}
		Collections.shuffle(presents);
		var nextPresentIndex = new AtomicInteger(0);

		// prepare the chain
		var chain = new LockFreeList<Integer>(
				new Node<Integer>(Integer.MIN_VALUE),
				new Node<Integer>(Integer.MAX_VALUE));

		// start the servants
		var servants = new ArrayList<Thread>();
		for (int i = 0; i < 8; i++) {
			var servant = new Thread(() -> {
				while (true) {
					// check if presents are exhausted
					int presentIndex = nextPresentIndex.getAndIncrement();
					if (presentIndex >= 500000) {
						break;
					}
					// if not, add a present to a chain
					int present = presents.get(presentIndex);
					chain.add(present);
					// write a thank you
					System.out.println("Thank you to guest #" + present);
					chain.remove(present);
				}
			});
			servants.add(servant);
			servant.start();
		}

		for (var servant : servants) {
			try {
				servant.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}

/*
 * Implementation adapted from The Art of Multiprocessor Programming (Herlihy
 * and Shavit)
 */
class LockFreeList<T> {
	public Node<T> head;
	public Node<T> tail;

	public LockFreeList(Node<T> head, Node<T> tail) {
		this.head = head;
		this.tail = tail;
		this.head.next = new AtomicMarkableReference<Node<T>>(tail, false);
	}

	public Window<T> find(Node<T> head, int key) {
		Node<T> pred = null;
		Node<T> curr = null;
		Node<T> succ = null;
		boolean[] marked = { false };
		boolean snip;
		retry: while (true) {
			pred = head;
			curr = pred.next.getReference();
			while (true) {
				if (curr == tail) {
					return new Window<T>(pred, curr);
				}
				succ = curr.next.get(marked);
				while (marked[0]) {
					snip = pred.next.compareAndSet(curr, succ, false, false);
					if (!snip) {
						continue retry;
					}
					if (succ == tail) {
						return new Window<T>(curr, succ);
					}
					curr = succ;
					succ = curr.next.get(marked);
				}
				if (curr.key >= key) {
					return new Window<T>(pred, curr);
				}
				pred = curr;
				curr = succ;
			}
		}
	}

	public boolean add(T item) {
		int key = item.hashCode();
		while (true) {
			Window<T> window = find(head, key);
			Node<T> pred = window.pred;
			Node<T> curr = window.curr;
			if (curr.key == key) {
				return false;
			} else {
				Node<T> node = new Node<T>(item);
				node.next = new AtomicMarkableReference<>(curr, false);
				if (pred.next.compareAndSet(curr, node, false, false)) {
					return true;
				}
			}
		}
	}

	public boolean remove(T item) {
		int key = item.hashCode();
		boolean snip;
		while (true) {
			Window<T> window = find(head, key);
			Node<T> pred = window.pred;
			Node<T> curr = window.curr;
			if (curr.key != key) {
				return false;
			} else {
				Node<T> succ = curr.next.getReference();
				snip = curr.next.compareAndSet(succ, succ, false, true);
				if (!snip) {
					continue;
				}
				pred.next.compareAndSet(curr, succ, false, false);
				return true;
			}
		}
	}

	public boolean contains(T item) {
		boolean[] marked = { false };
		int key = item.hashCode();
		Node<T> curr = head;
		while (curr.key < key) {
			curr = curr.next.getReference();
		}
		return curr.key == key && !marked[0];
	}
}

class Node<T> {
	T item;
	int key;
	AtomicMarkableReference<Node<T>> next;

	public Node(T item) {
		this.item = item;
		this.key = item.hashCode();
	}
}

class Window<T> {
	public Node<T> pred, curr;

	public Window(Node<T> pred, Node<T> curr) {
		this.pred = pred;
		this.curr = curr;
	}
}
