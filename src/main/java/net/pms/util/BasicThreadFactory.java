/*
 * Digital Media Server, for streaming digital media to UPnP AV or DLNA
 * compatible devices based on PS3 Media Server and Universal Media Server.
 * Copyright (C) 2016 Digital Media Server developers.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see http://www.gnu.org/licenses/.
 */
package net.pms.util;

import static org.apache.commons.lang3.StringUtils.isBlank;
import java.util.Locale;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * A {@link ThreadFactory} implementing a simple name structure.
 *
 * @author Nadahar
 */
public class BasicThreadFactory implements ThreadFactory {
	/** A static thread-safe pool number counter. */
	protected static final AtomicInteger POOL_NUMBER = new AtomicInteger(1);

	/** The pool number for this instance. */
	protected final int instancePoolNumber;

	/** The {@link ThreadGroup}. */
	protected final ThreadGroup group;

	/** The thread number counter. */
	protected final AtomicInteger threadNumber = new AtomicInteger(1);

	/** The thread priority. */
	protected final int threadPriority;

	/** The name pattern. */
	protected final String namePattern;

	/** The number of variables in {@code namePattern}. */
	protected final int numVariables;

	/**
	 * Creates a new {@link BasicThreadFactory} using the default
	 * {@link ThreadGroup} and priority {@link Thread#NORM_PRIORITY}.
	 * <p>
	 * The {@link Thread} names generated by the new {@link BasicThreadFactory}
	 * is created by calling {@link String#format} with {@code namePattern} as
	 * the "format" and pool- and thread number as arguments. The formatting
	 * rules are those of {@link java.util.Formatter}.
	 * <p>
	 * No more than two variables of type {@code %d} or {@code %s} is allowed in
	 * {@code namePattern}, and they will be substituted as follows:
	 * <ul>
	 * <li>No variables: All {@link Thread} names generated by this
	 * {@link ThreadFactory} will be equal to {@code namePattern}.</li>
	 * <li>One variable: Only thread number will be used, pool number isn't
	 * used.</li>
	 * <li>Two variables: Pool number will be used for the first variable in
	 * {@code namePattern}, thread number for the second.
	 * </ul>
	 *
	 * @param namePattern The {@link java.util.Formatter} formatted
	 *            {@link String} from which to generate {@link Thread} names.
	 */
	public BasicThreadFactory(String namePattern) {
		this(namePattern, Thread.NORM_PRIORITY, null);
	}

	/**
	 * Creates a new {@link BasicThreadFactory} using the given arguments and
	 * the default {@link ThreadGroup}.
	 * <p>
	 * The {@link Thread} names generated by the new {@link BasicThreadFactory}
	 * is created by calling {@link String#format} with {@code namePattern} as
	 * the "format" and pool- and thread number as arguments. The formatting
	 * rules are those of {@link java.util.Formatter}.
	 * <p>
	 * No more than two variables of type {@code %d} or {@code %s} is allowed in
	 * {@code namePattern}, and they will be substituted as follows:
	 * <ul>
	 * <li>No variables: All {@link Thread} names generated by this
	 * {@link ThreadFactory} will be equal to {@code namePattern}.</li>
	 * <li>One variable: Only thread number will be used, pool number isn't
	 * used.</li>
	 * <li>Two variables: Pool number will be used for the first variable in
	 * {@code namePattern}, thread number for the second.
	 * </ul>
	 *
	 * @param namePattern The {@link java.util.Formatter} formatted
	 *            {@link String} from which to generate {@link Thread} names.
	 * @param threadPriority The {@link Thread} priority.
	 */
	public BasicThreadFactory(String namePattern, int threadPriority) {
		this(namePattern, threadPriority, null);
	}

	/**
	 * Creates a new {@link BasicThreadFactory} using the given arguments and
	 * priority {@link Thread#NORM_PRIORITY}.
	 * <p>
	 * The {@link Thread} names generated by the new {@link BasicThreadFactory}
	 * is created by calling {@link String#format} with {@code namePattern} as
	 * the "format" and pool- and thread number as arguments. The formatting
	 * rules are those of {@link java.util.Formatter}.
	 * <p>
	 * No more than two variables of type {@code %d} or {@code %s} is allowed in
	 * {@code namePattern}, and they will be substituted as follows:
	 * <ul>
	 * <li>No variables: All {@link Thread} names generated by this
	 * {@link ThreadFactory} will be equal to {@code namePattern}.</li>
	 * <li>One variable: Only thread number will be used, pool number isn't
	 * used.</li>
	 * <li>Two variables: Pool number will be used for the first variable in
	 * {@code namePattern}, thread number for the second.
	 * </ul>
	 *
	 * @param namePattern The {@link java.util.Formatter} formatted
	 *            {@link String} from which to generate {@link Thread} names.
	 * @param group The {@link ThreadGroup}.
	 */
	public BasicThreadFactory(String namePattern, ThreadGroup group) {
		this(namePattern, Thread.NORM_PRIORITY, group);
	}

	/**
	 * Creates a new {@link BasicThreadFactory} using the given arguments.
	 * <p>
	 * The {@link Thread} names generated by the new {@link BasicThreadFactory}
	 * is created by calling {@link String#format} with {@code namePattern} as
	 * the "format" and pool- and thread number as arguments. The formatting
	 * rules are those of {@link java.util.Formatter}.
	 * <p>
	 * No more than two variables of type {@code %d} or {@code %s} is allowed in
	 * {@code namePattern}, and they will be substituted as follows:
	 * <ul>
	 * <li>No variables: All {@link Thread} names generated by this
	 * {@link ThreadFactory} will be equal to {@code namePattern}.</li>
	 * <li>One variable: Only thread number will be used, pool number isn't
	 * used.</li>
	 * <li>Two variables: Pool number will be used for the first variable in
	 * {@code namePattern}, thread number for the second.
	 * </ul>
	 *
	 * @param namePattern The {@link java.util.Formatter} formatted
	 *            {@link String} from which to generate {@link Thread} names.
	 * @param threadPriority The {@link Thread} priority.
	 * @param group The {@link ThreadGroup}.
	 */
	public BasicThreadFactory(String namePattern, int threadPriority, ThreadGroup group) {
		if (isBlank(namePattern)) {
			throw new IllegalArgumentException("namePattern cannot be blank");
		}
		if (group == null) {
			SecurityManager securityManager = System.getSecurityManager();
			group = (securityManager != null) ?
				securityManager.getThreadGroup() :
				Thread.currentThread().getThreadGroup();
		}
		this.group = group;
		this.threadPriority = Math.min(Thread.MAX_PRIORITY, Math.max(Thread.MIN_PRIORITY, threadPriority));
		int pctSes = 0;
		int pctDs = 0;
		int i = 0;
		while (true) {
			i = namePattern.indexOf("%s", i);
			if (i >= 0) {
				pctSes++;
				i++;
			} else {
				break;
			}
		}
		while (true) {
			i = namePattern.indexOf("%d", i);
			if (i >= 0) {
				pctDs++;
				i++;
			} else {
				break;
			}
		}
		if (pctSes + pctDs > 2) {
			throw new IllegalArgumentException("namePattern can't have more than 2 variables");
		}
		this.numVariables = pctSes + pctDs;
		this.namePattern = namePattern;
		if (numVariables == 2) {
			this.instancePoolNumber = POOL_NUMBER.getAndIncrement();
		} else {
			this.instancePoolNumber = 0;
		}
	}

	@Override
	public Thread newThread(Runnable runnable) {
		String threadName;
		switch (numVariables) {
			case 0:
				threadName = namePattern;
				break;
			case 1:
				threadName = String.format(Locale.ROOT, namePattern, threadNumber.getAndIncrement());
				break;
			default:
				threadName = String.format(Locale.ROOT, namePattern, instancePoolNumber, threadNumber.getAndIncrement());
		}
		Thread thread = new Thread(
			group,
			runnable,
			threadName,
			0
		);
		if (thread.isDaemon()) {
			thread.setDaemon(false);
		}
		if (thread.getPriority() != threadPriority) {
			thread.setPriority(threadPriority);
		}
		return thread;
	}

}
