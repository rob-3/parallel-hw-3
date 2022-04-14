# COP 4520 Spring 2022 Assignment 3

> Using git to track last edit date is **NOT** a reliable measure ([ref](https://github.com/rob-3/git-time-demo)). I did not change the dates on this submission, but the graders and Dr. Dechev should be aware that anyone familiar with git can trivially manipulate the dates on git commits.

This program is written in Java. [Install Java](https://java.com/en/download/help/download_options.html), then run the program with:

```bash
javac Assignment3.java && java Assignment3
```

in the root of the project.

If you wish to avoid console spam, feel free to pipe output to `/dev/null` or a file. You can also easily comment out one of the question's code to run only the other.

```java
javac Assignment3.java && java Assignment3 > /dev/null
```

# Problem 1

> Unfortunately, the servants realized at the end of the day that they had more presents than “Thank you” notes. What could have gone wrong?

If one servant linked a present B to a present A as another servant was removing present A, then present B would have been removed before a thank you note was written.

> Can we help the Minotaur and his servants improve their strategy for writing “Thank you” notes?

Yes. See the implementation. Checks must be made to ensure this has not happened.

# Problem 2

I use a lock-free approach to implementing the reporting system. Each thread independently takes measurements, and then atomically compares to sorted values in three arrays of length 4. If the value is extreme enough to warrant recording, the exact location is optimistically located, and a `.compareAndSet()` attempted, with appropriate handling if the value has been overwritten.

> Discuss the efficiency ... of your design.

This implementation is highly efficient, as the vast majority of sensor readings simply check the first value of each array and realize they aren't notable enough to record. Given the relatively few writes in the problem, a lock-free, optimistic design makes sense. When testing for failed `.compareAndSet()`s, usually less than 20 occurred total on my machine.

> Discuss the ... correctness ... of your design.

The invariant maintained is that the array will stay sorted. This is guaranteed because a set will only be attempted in the exact sorted location for the new reading. If the set fails, this location is recalculated. Multiple simultaneous sets to the same location cannot break due to `.compareAndSet()`. Multiple simultaneous sets to different locations maintain the invariant, since an element that is being inserted further into the array must necessarily be more extreme than one being inserted earlier.

> Discuss the ... progress guarantee of your design.

Because the algorithm is lock-free, there is a systemwide progress guarantee. There is not a per thread guarantee of progress, but as shown experimentally, failed `.compareAndSet()s` are not common. The scenario where a thread could be starved is if it get stuck reading a value, another thread writes to that value, and then a `.compareAndSet()` is attempted, over and over. This is vanishingly unlikely, since the vast majority of sensor readings don't even attempt a write because they aren't notable.
