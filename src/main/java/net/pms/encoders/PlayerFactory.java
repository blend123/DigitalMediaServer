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
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import net.pms.PMS;
import net.pms.configuration.PmsConfiguration;
import net.pms.dlna.DLNAResource;
import net.pms.formats.FormatFactory;
import net.pms.io.SystemUtils;
import net.pms.util.FilePermissions;
import net.pms.util.FileUtil;
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

	@Deprecated
	public static void initialize(final PmsConfiguration configuration) {
		initialize();
	}

	/**
	 * Constructor that registers all players based on the given configuration,
	 * frame and registry.
	 */
	public static void initialize() {
		utils = PMS.get().getRegistry();
		registerPlayers();
	}

	/**
	 * Register a known set of audio or video transcoders.
	 */
	private static void registerPlayers() {
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
	}

	/**
	 * Adds a single {@link Player} to the list of Players. Before the player is
	 * added to the list, it is verified to be okay.
	 *
	 * @param player Player to be added to the list.
	 */
	public static void registerPlayer(final Player player) {
		configuration.capitalizeEngineId(player);
		playersLock.writeLock().lock();
		try {
			if (players.contains(player)) {
				LOGGER.info("Transcoding engine {} already exists, skipping registering...", player);
				return;
			}

			boolean ok = false;
			players.add(player);
			player.setEnabled(configuration.isEngineEnabled(player));

			if (Player.NATIVE.equals(player.executable())) {
				player.setAvailable(true);
				ok = true;
			} else {
				if (player.executable() == null) {
					player.setAvailable(false);
					LOGGER.warn("Executable of transcoding engine {} is undefined", player);
					return;
				}

				Path executable;
				if (Platform.isWindows()) {
					String[] validExtensions = {"exe", "com", "bat"};
					String extension = FileUtil.getExtension(player.executable());
					if (extension == null || !Arrays.asList(validExtensions).contains(extension.toLowerCase())) {
						executable = Paths.get(player.executable() + ".exe");
					} else {
						executable = Paths.get(player.executable());
					}
				} else if (player.avisynth()) {
					LOGGER.debug("Skipping transcoding engine {} as it's not compatible with this platform");
					player.setAvailable(false);
					return;
				} else {
					executable = Paths.get(player.executable());
				}

				try {
					FilePermissions permissions = new FilePermissions(executable);
					ok = permissions.isExecutable();
					if (!ok) {
						LOGGER.warn("Insufficient permission to execute \"{}\" for transcoding engine {}", executable.toAbsolutePath(), player);
					} else if (Platform.isWindows() && player.avisynth()) {
						ok = utils.isAvis();
						if (!ok) {
							LOGGER.warn("Transcoding engine {} is unavailable since AviSynth couldn't be found", player);
						}
					}
				} catch (FileNotFoundException e) {
					LOGGER.warn("Executable \"{}\" of transcoding engine {} not found: {}", executable.toAbsolutePath(), player, e.getMessage());
					LOGGER.trace("", e);
				}
			}

			player.setAvailable(ok);
			if (ok) {
				LOGGER.info("Registering transcoding engine: {}", player);
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
	public static boolean isPlayerActive(String id) {
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
					} else {
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
	 * @deprecated Use {@link #getPlayers(DLNAResource)} instead.
	 *
	 * @param resource The resource to match
	 * @return The list of players if a match could be found, null otherwise.
	 */
	@Deprecated
	public static ArrayList<Player> getEnabledPlayers(final DLNAResource resource) {
		return getPlayers(resource);
	}
}
