/*
 * PS3 Media Server, for streaming any medias to your PS3.
 * Copyright (C) 2008  A.Brochard
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package net.pms.encoders;

import com.sun.jna.Platform;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.pms.Messages;
import net.pms.PMS;
import net.pms.configuration.PlatformExecutableInfo;
import net.pms.configuration.PmsConfiguration;
import net.pms.configuration.ProgramExecutableType;
import net.pms.dlna.DLNAResource;
import net.pms.formats.FormatFactory;
import net.pms.io.SimpleProcessWrapper;
import net.pms.io.SimpleProcessWrapper.SimpleProcessWrapperResult;
import net.pms.io.SystemUtils;
import net.pms.util.FilePermissions;
import net.pms.util.FileUtil;
import net.pms.util.StringUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class handles players. Creating an instance will initialize the list of
 * known players.
 *
 * @since 1.51.0
 */
public final class PlayerFactory {
	/**
	 * Logger used for all logging.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(FormatFactory.class);

	/**
	 * Must be use to lock all access to {@link #players}.
	 */
	private static final ReentrantReadWriteLock playersLock = new ReentrantReadWriteLock();

	/**
	 * List of all {@link Player} objects.
	 */
	private static final ArrayList<Player> players = new ArrayList<>();

	/**
	 * Interface to Windows specific functions, like Windows Registry. The
	 * registry is set by the constructor.
	 */
	private static SystemUtils utils;

	private static PmsConfiguration configuration = PMS.getConfiguration();

	/**
	 * This takes care of sorting the players by the given PMS configuration.
	 */
	private static class PlayerSort implements Comparator<Player>, Serializable {

		private static final long serialVersionUID = 7847954072031859756L;

		@Override
		public int compare(Player player1, Player player2) {
			Integer index1 = configuration.getEnginePriority(player1);
			Integer index2 = configuration.getEnginePriority(player2);

			// Not being in the priority list will sort the player as last.
			if (index1 == -1) {
				index1 = 999;
			}

			if (index2 == -1) {
				index2 = 999;
			}

			return index1.compareTo(index2);
		}
	}

	/**
	 * This class is not meant to be instantiated.
	 */
	private PlayerFactory() {
	}

	/**
	 * Constructor that registers all players based on the given configuration,
	 * frame and registry.
	 * @throws InterruptedException
	 */
	public static void initialize() throws InterruptedException {
		utils = PMS.get().getRegistry();
		registerPlayers();
	}

	/**
	 * Register a known set of transcoding engines.
	 * @throws InterruptedException
	 */
	private static void registerPlayers() throws InterruptedException {
		if (Platform.isWindows()) {
			registerPlayer(new AviSynthFFmpeg());
			registerPlayer(new AviSynthMEncoder());
			registerPlayer(new FFmpegDVRMSRemux());
		}

		registerPlayer(new FFmpegAudio());
		registerPlayer(new MEncoderVideo());
		registerPlayer(new FFMpegVideo());
		registerPlayer(new VLCVideo());
		registerPlayer(new FFmpegWebVideo());
		registerPlayer(new MEncoderWebVideo());
		registerPlayer(new VLCWebVideo());
		registerPlayer(new TsMuxeRVideo());
		registerPlayer(new TsMuxeRAudio());
		registerPlayer(new VideoLanAudioStreaming());
		registerPlayer(new VideoLanVideoStreaming());
		registerPlayer(new RAWThumbnailer());

		FFmpegWebVideo.initializeProtocols();
	}

	/**
	 * Adds a single {@link Player} to the list of Players. Before the player is
	 * added to the list, it is verified to be okay.
	 *
	 * @param player Player to be added to the list.
	 * @throws InterruptedException
	 */
	public static void registerPlayer(final Player player) throws InterruptedException {
		configuration.capitalizeEngineId(player);
		playersLock.writeLock().lock();
		try {
			if (players.contains(player)) {
				LOGGER.info("Transcoding engine {} already exists, skipping registration...", player);
				return;
			}

			LOGGER.info("Checking transcoding engine: {}", player);
			players.add(player);
			player.setEnabled(configuration.isEngineEnabled(player), false);

			PlatformExecutableInfo executableInfo = player.executables();
			ReentrantReadWriteLock executableInfoLock = executableInfo.getLock();
			// Lock it for consistency during tests
			executableInfoLock.readLock().lock();
			try {
				for (ProgramExecutableType executableType : executableInfo.executablesTypes()) {

					if (executableInfo.getPath(executableType) == null) {
						player.setUnavailable(
							executableType,
							String.format(Messages.getString("Engine.ExecutableNotDefined"),player)
						);
						LOGGER.warn("{} executable of transcoding engine {} is undefined", executableType, player);
						continue;
					}

					Path executable;
					if (Platform.isWindows()) {
						String[] validExtensions = {"exe", "com", "bat"};
						String extension = FileUtil.getExtension(executableInfo.getPath(executableType));
						if (extension == null || !Arrays.asList(validExtensions).contains(extension.toLowerCase())) {
							executable = Paths.get(executableInfo.getPath(executableType) + ".exe");
						} else {
							executable = Paths.get(executableInfo.getPath(executableType));
						}
					} else if (player.avisynth()) {
						LOGGER.debug("Skipping transcoding engine {} ({}) as it's not compatible with this platform", player, executableType);
						player.setUnavailable(executableType, String.format(Messages.getString("Engine.ExecutablePlatformIncompatible"), player));
						continue;
					} else {
						executable = Paths.get(executableInfo.getPath(executableType));
					}

					/*
					 * Don't make executables that only is a file name absolute
					 * or check for file permissions as they probably depend on
					 * the local path.
					 */
					if (!executable.equals(executable.getFileName())) {
						executable = executable.toAbsolutePath();

						try {
							FilePermissions permissions = new FilePermissions(executable);
							if (!permissions.isExecutable()) {
								LOGGER.warn("Insufficient permission to execute \"{}\" for transcoding engine {} ({})", executable, player, executableType);
								player.setUnavailable(executableType, String.format(Messages.getString("Engine.MissingExecutePermission"), executable, player));
								continue;
							}
						} catch (FileNotFoundException e) {
							LOGGER.warn("{} executable \"{}\" of transcoding engine {} not found: {}", executableType, executable, player, e.getMessage());
							player.setUnavailable(executableType, String.format(Messages.getString("Engine.ExecutableNotFound"), executable, player));
							continue;
						}
					}
					if (Platform.isWindows() && player.avisynth() && !utils.isAvis()) {
						LOGGER.debug("Transcoding engine {} ({}) is unavailable since AviSynth couldn't be found", player, executableType);
						player.setUnavailable(executableType, String.format(Messages.getString("Engine.AviSynthNotFound"), player));
						continue;
					}
					if (!playerTest(player, executableType, executable)) {
						// Only set available if this isn't already done by the test to avoid overwriting the state
						player.setAvailable(executableType, null);
					}
				}

				// Find the best executable type to use, first read the one from configuration
				ProgramExecutableType currentExecutableType = configuration.getExecutableType(player.id());
				if (!player.isAvailable(currentExecutableType)) {
					// Set the platform default if that is available
					if (player.isAvailable(executableInfo.getDefault())) {
						currentExecutableType = executableInfo.getDefault();
					} else {
						// Set the first one that is available, if any
						for (ProgramExecutableType executableType : executableInfo.executablesTypes()) {
							if (player.isAvailable(executableType)) {
								currentExecutableType = executableType;
								break;
							}
						}
					}
					// Leave it to the configured type if none other is available
				}
				player.setCurrentExecutableType(currentExecutableType, false);

			} finally {
				executableInfoLock.readLock().unlock();
			}

			if (player.isAvailable()) {
				LOGGER.info("Transcoding engine \"{}\" is available", player);
			} else {
				LOGGER.warn("Transcoding engine \"{}\" is not available", player);
			}

			// Sort the players according to the configuration settings. This
			// will have to be after each registered player in case a player is
			// registered at a later stage than during initialization.
			sortPlayers();

		} finally {
			playersLock.writeLock().unlock();
		}
	}

	/**
	 * Used to (re)sort {@link #players} every time either {@link #players}
	 * or {@link PmsConfiguration#enginesPriority} has changed so that
	 * {@link #players} always is sorted according to priority.
	 */
	public static void sortPlayers() {
		playersLock.writeLock().lock();
		try {
			Collections.sort(players, new PlayerSort());
		} finally {
			playersLock.writeLock().unlock();
		}
	}

	/**
	 * Returns the list of all players. This includes the ones not verified as
	 * being okay.
	 *
	 * @return The list of players.
	 */
	public static ArrayList<Player> getAllPlayers() {
		playersLock.readLock().lock();
		try {
			return new ArrayList<Player>(players);
		} finally {
			playersLock.readLock().unlock();
		}
	}

	/**
	 * Returns the list of players according to the given filters ordered by
	 * priority.
	 *
	 * @return The list of players.
	 */
	public static ArrayList<Player> getPlayers(boolean onlyEnabled, boolean onlyAvailable) {
		playersLock.readLock().lock();
		try {
			ArrayList<Player> resultPlayers = new ArrayList<>();
			for (Player player : players) {
				if ((!onlyAvailable || player.isAvailable()) && (!onlyEnabled || player.isEnabled())) {
					resultPlayers.add(player);
				}
			}
			return resultPlayers;
		} finally {
			playersLock.readLock().unlock();
		}
	}

	/**
	 * Returns the list of enabled and available players ordered by priority.
	 *
	 * @return The list of players.
	 */
	public static ArrayList<Player> getPlayers() {
		return getPlayers(true, true);
	}

	/**
	 * Checks if a {@link Player} of the given type is both available and enabled.
	 * @param id the {@link Player} type to check for
	 * @return the result
	 */
	public static boolean isPlayerActive(PlayerId id) {
		if (id == null) {
			throw new NullPointerException("id cannot be null");
		}

		playersLock.readLock().lock();
		try {
			for (Player player : players) {
				if (player.id().equals(id)) {
					return player.isActive();
				}
			}
			return false;
		} finally {
			playersLock.readLock().unlock();
		}

	}
	/**
	 * Returns the first {@link Player} that matches the given mediaInfo or
	 * format. Each of the available players is passed the provided information
	 * and the first that reports it is compatible will be returned.
	 *
	 * @param resource
	 *            The {@link DLNAResource} to match
	 * @return The player if a match could be found, <code>null</code>
	 *         otherwise.
	 * @since 1.60.0
	 */
	public static Player getPlayer(final DLNAResource resource) {
		if (resource == null) {
			LOGGER.warn("Invalid resource (null): no player found");
			return null;
		} else {
			LOGGER.trace("Getting player for resource \"{}\"", resource.getName());
		}

		playersLock.readLock().lock();
		try {
			for (Player player : players) {
				boolean enabled = player.isEnabled();
				boolean available = player.isAvailable();
				if (enabled && available) {
					boolean compatible = player.isCompatible(resource);

					if (compatible) {
						// Player is enabled and compatible
						LOGGER.trace("Returning compatible player \"{}\"", player.name());
						return player;
					} else if (LOGGER.isTraceEnabled()) {
						LOGGER.trace("Player \"{}\" is incompatible", player.name());
					}
				} else if (LOGGER.isTraceEnabled()) {
					if (available) {
						LOGGER.trace("Player \"{}\" is disabled", player.name());
					} else if (enabled) {
						LOGGER.trace("Player \"{}\" isn't available", player.name());
					} else {
						LOGGER.trace("Player \"{}\" is neither available nor enabled", player.name());
					}
				}
			}
		} finally {
			playersLock.readLock().unlock();
		}

		LOGGER.trace("No player found for {}", resource.getName());

		return null;
	}

	/**
	 * Gets a {@link Player} from the registered players or {@code null} if
	 * the no such {@link Player} is registered.
	 *
	 * @param id the {@link PlayerId} to use for lookup.
	 * @return the {@link Player} or {@code null}.
	 */
	public static Player getPlayer(final PlayerId id) {
		if (id == null) {
			return null;
		}

		playersLock.readLock().lock();
		try {
			for (Player player : players) {
				if (id.equals(player.id())) {
					return player;
				}
			}
		} finally {
			playersLock.readLock().unlock();
		}
		return null;
	}

	/**
	 * Gets a {@link Player}s currently active executable the registered
	 * players or {@code null} if the no such {@link Player} is registered.
	 *
	 * @param id the {@link PlayerId} to use for lookup.
	 * @return the executable {@link String} or {@code null}.
	 */
	public static String getPlayerExecutable(final PlayerId id) {
		if (id == null) {
			return null;
		}

		playersLock.readLock().lock();
		try {
			for (Player player : players) {
				if (id.equals(player.id())) {
					return player.executable();
				}
			}
		} finally {
			playersLock.readLock().unlock();
		}
		return null;
	}

	/**
	 * Returns all {@link Player}s that match the given resource and are
	 * enabled. Each of the available players is passed the provided information
	 * and each player that reports it is compatible will be returned.
	 *
	 * @param resource
	 *        The {@link DLNAResource} to match
	 * @return The list of compatible players if a match could be found,
	 *         <code>null</code> otherwise.
	 * @since 1.60.0
	 */
	public static ArrayList<Player> getPlayers(final DLNAResource resource) {
		if (resource == null) {
			return null;
		}

		ArrayList<Player> compatiblePlayers = new ArrayList<>();

		playersLock.readLock().lock();
		try {
			for (Player player : players) {
				if (player.isEnabled() && player.isAvailable() && player.isCompatible(resource)) {
					// Player is enabled, available and compatible
					LOGGER.trace("Player {} is compatible with resource \"{}\"", player.name(), resource.getName());
					compatiblePlayers.add(player);
				}
			}
		} finally {
			playersLock.readLock().unlock();
		}

		return compatiblePlayers;
	}

	/**
	 * Protected by {@link #playersLock}
	 */
	private static final List<PlayerTestRecord> testRecords = new ArrayList<>();

	/**
	 * Must only be called when a lock is held on {@link #playersLock}
	 * @param player the player whose executable to test
	 *
	 * @return {@code true} if a test was performed, {@code false} otherwise.
	 * @throws InterruptedException
	 */
	private static boolean playerTest(Player player, ProgramExecutableType executableType, Path executable) throws InterruptedException {
		if (executable == null) {
			return false;
		}

		for (PlayerTestRecord testRecord : testRecords) {
			if (executable.equals(testRecord.executable)) {
				player.setAvailable(testRecord.pass, executableType, testRecord.result);
				return true;
			}
		}

		// Return true if a test is performed and availability is set
		if (player instanceof FFMpegVideo || player instanceof FFmpegDVRMSRemux) {
			final String arg = "-version";
			String state = null;
			try {
				SimpleProcessWrapperResult result = SimpleProcessWrapper.runProcess(executable.toString(), arg);
				if (result.exitCode == 0) {
					if (result.output != null && result.output.size() > 0) {
						Pattern pattern = Pattern.compile("^ffmpeg version\\s+(.*?)\\s+Copyright", Pattern.CASE_INSENSITIVE);
						Matcher matcher = pattern.matcher(result.output.get(0));
						if (matcher.find()) {
							state = matcher.group(1);
							player.setAvailable(executableType, state);
						} else {
							player.setAvailable(executableType, null);
						}
					} else {
						player.setAvailable(executableType, null);
					}
				} else {
					if (result.output.size() > 2) {
						state =
							String.format(Messages.getString("Engine.Error"), player) + " \n" +
							result.output.get(result.output.size() - 2) + " " +
							result.output.get(result.output.size() - 1);
						player.setUnavailable(executableType, state);
					} else if (result.output.size() > 1) {
						state =
							String.format(Messages.getString("Engine.Error"), player) + " \n" +
							result.output.get(result.output.size() - 1);
						player.setUnavailable(executableType, state);
					} else {
						state = String.format(Messages.getString("Engine.Error"), player) + Messages.getString("General.3");
						player.setUnavailable(executableType, state);
					}
				}
			} catch (IOException e) {
				LOGGER.debug("\"{} {}\" ({}) failed with error: {}", executable, arg, executableType, e.getMessage());
				state = String.format(Messages.getString("Engine.Error"), player) + " \n" + e.getMessage();
				player.setUnavailable(executableType, state);
			}
			testRecords.add(new PlayerTestRecord(executable, player.isAvailable(executableType) , state));
			return true;
		} else if (player instanceof MEncoderVideo || player instanceof MEncoderWebVideo) {
			final String arg = "-info:help";
			String state = null;
			try {
				SimpleProcessWrapperResult result = SimpleProcessWrapper.runProcess(executable.toString(), arg);
				if (result.exitCode == 0) {
					if (result.output != null && result.output.size() > 0) {
						Pattern pattern = Pattern.compile("^MEncoder\\s+(.*?)\\s+\\(C\\)", Pattern.CASE_INSENSITIVE);
						Matcher matcher = pattern.matcher(result.output.get(0));
						if (matcher.find()) {
							state = matcher.group(1);
							player.setAvailable(executableType, state);
						} else {
							player.setAvailable(executableType, null);
						}
					} else {
						player.setAvailable(executableType, null);
					}
				} else {
					if (result.output != null &&
						result.output.size() > 3 &&
						StringUtil.hasValue(result.output.get(result.output.size() - 1)) &&
						!StringUtil.hasValue(result.output.get(result.output.size() - 2)) &&
						StringUtil.hasValue(result.output.get(result.output.size() - 3))
					) {
						state =
							String.format(Messages.getString("Engine.Error"), player) + " \n" +
							result.output.get(result.output.size() - 3);
						player.setUnavailable(executableType, state);
					} else {
						state = String.format(Messages.getString("Engine.Error"), player) + Messages.getString("General.3");
						player.setUnavailable(executableType, state);
					}
				}
			} catch (IOException e) {
				LOGGER.debug("\"{} {}\" ({}) failed with error: {}", executable, arg, executableType, e.getMessage());
				state = String.format(Messages.getString("Engine.Error"), player) + " \n" + e.getMessage();
				player.setUnavailable(executableType, state);
			}
			testRecords.add(new PlayerTestRecord(executable, player.isAvailable(executableType), state));
			return true;
		} else if (player instanceof TsMuxeRVideo) {
			final String arg = "-v";
			String state = null;
			try {
				SimpleProcessWrapperResult result = SimpleProcessWrapper.runProcess(executable.toString(), arg);
				if (result.exitCode == 0) {
					if (result.output != null && result.output.size() > 0) {
						Pattern pattern = Pattern.compile("tsMuxeR\\.\\s+Version\\s(\\S+)\\s+", Pattern.CASE_INSENSITIVE);
						Matcher matcher = pattern.matcher(result.output.get(0));
						if (matcher.find()) {
							state = matcher.group(1);
							player.setAvailable(executableType, state);
						} else {
							player.setAvailable(executableType, null);
						}
					} else {
						player.setAvailable(executableType, null);
					}
				} else {
					state = String.format(Messages.getString("Engine.ExitCode"), player, result.exitCode);
					if (Platform.isLinux() && Platform.is64Bit()) {
						state += ". \n" + Messages.getString("Engine.tsMuxerErrorLinux");
					}
					player.setUnavailable(executableType, state);
				}
			} catch (IOException e) {
				LOGGER.debug("\"{} {}\" ({}) failed with error: {}", executable, arg, executableType, e.getMessage());
				state = String.format(Messages.getString("Engine.Error"), player) + " \n" + e.getMessage();
				player.setUnavailable(executableType, state);
			}
			testRecords.add(new PlayerTestRecord(executable, player.isAvailable(executableType), state));
			return true;
		} else if (player instanceof RAWThumbnailer) {
			String state = null;
			try {
				SimpleProcessWrapperResult result = SimpleProcessWrapper.runProcess(executable.toString());
				if (result.output != null && StringUtils.isBlank(result.output.get(0))) {
					if (result.output.size() > 1) {
						Pattern pattern = Pattern.compile("decoder\\s\"dcraw\"\\s(\\S+)", Pattern.CASE_INSENSITIVE);
						Matcher matcher = pattern.matcher(result.output.get(1));
						if (matcher.find()) {
							state = matcher.group(1);
							player.setAvailable(executableType, state);
						} else {
							player.setAvailable(executableType, null);
						}
					} else {
						player.setAvailable(executableType, null);
					}
				} else if (result.output != null && result.output.size() > 0){
					state =
						String.format(Messages.getString("Engine.Error"), player) + " \n" +
						result.output.get(0);
					player.setUnavailable(executableType, state);
				} else {
					state = String.format(Messages.getString("Engine.Error"), player) + Messages.getString("General.3");
					player.setUnavailable(executableType, state);
				}
			} catch (IOException e) {
				LOGGER.debug("\"{}\" ({}) failed with error: {}", executable, executableType, e.getMessage());
				state = String.format(Messages.getString("Engine.Error"), player) + " \n" + e.getMessage();
				player.setUnavailable(executableType, state);
			}
			testRecords.add(new PlayerTestRecord(executable, player.isAvailable(executableType), state));
			return true;
		}
		// No test has been made for VLC, found no way to get feedback on stdout: https://forum.videolan.org/viewtopic.php?t=73665

		return false;
	}

	/**
	 * @deprecated Use {@link #getPlayers(DLNAResource)} instead.
	 *
	 * @param resource The resource to match
	 * @return The list of players if a match could be found, null otherwise.
	 */
	@Deprecated
	public static ArrayList<Player> getEnabledPlayers(final DLNAResource resource) {
		return getPlayers(resource);
	}

	private static class PlayerTestRecord {
		public final Path executable;
		public final boolean pass;
		public final String result;

		public PlayerTestRecord(Path executable, boolean pass, String result) {
			this.executable = executable;
			this.pass = pass;
			this.result = result;
		}
	}
}
