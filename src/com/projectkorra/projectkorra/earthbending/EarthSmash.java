package com.projectkorra.projectkorra.earthbending;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AirAbility;
import com.projectkorra.projectkorra.ability.EarthAbility;
import com.projectkorra.projectkorra.ability.WaterAbility;
import com.projectkorra.projectkorra.avatar.AvatarState;
import com.projectkorra.projectkorra.util.ClickType;
import com.projectkorra.projectkorra.util.ParticleEffect;
import com.projectkorra.projectkorra.util.TempBlock;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EarthSmash extends EarthAbility {
	
	public static enum State {
		START, LIFTING, LIFTED, GRABBED, SHOT, FLYING, REMOVED
	}

	private boolean allowGrab;
	private boolean allowFlight;
	private int animationCounter;
	private int progressCounter;
	private int requiredBendableBlocks;
	private int maxBlocksToPassThrough;
	private long delay;
	private long cooldown;
	private long chargeTime;
	private long removeTimer;
	private long flightRemoveTimer;
	private long flightStartTime;
	private long shootAnimationInterval;
	private long flightAnimationInterval;
	private long liftAnimationInterval;
	private double selectRange;
	private double grabRange;
	private double shootRange;
	private double damage;
	private double knockback;
	private double knockup;
	private double flightSpeed;
	private double grabbedDistance;
	private double grabDetectionRadius;
	private double flightDetectionRadius;
	private State state;
	private Block origin;
	private Location location;
	private Location destination;
	private ArrayList<Entity> affectedEntities;
	private ArrayList<BlockRepresenter> currentBlocks;
	private ArrayList<TempBlock> affectedBlocks;

	public EarthSmash(Player player, ClickType type) {
		super(player);
		
		this.state = State.START;
		this.requiredBendableBlocks = getConfig().getInt("Abilities.Earth.EarthSmash.RequiredBendableBlocks");
		this.maxBlocksToPassThrough = getConfig().getInt("Abilities.Earth.EarthSmash.MaxBlocksToPassThrough");
		this.shootAnimationInterval = getConfig().getLong("Abilities.Earth.EarthSmash.ShootAnimationInterval");
		this.flightAnimationInterval = getConfig().getLong("Abilities.Earth.EarthSmash.FlightAnimationInterval");
		this.liftAnimationInterval = getConfig().getLong("Abilities.Earth.EarthSmash.LiftAnimationInterval");
		this.grabDetectionRadius = getConfig().getDouble("Abilities.Earth.EarthSmash.GrabDetectionRadius");
		this.flightDetectionRadius = getConfig().getDouble("Abilities.Earth.EarthSmash.FlightDetectionRadius");
		this.allowGrab = getConfig().getBoolean("Abilities.Earth.EarthSmash.AllowGrab");
		this.allowFlight = getConfig().getBoolean("Abilities.Earth.EarthSmash.AllowFlight");
		this.selectRange = getConfig().getDouble("Abilities.Earth.EarthSmash.SelectRange");
		this.grabRange = getConfig().getDouble("Abilities.Earth.EarthSmash.GrabRange");
		this.shootRange = getConfig().getDouble("Abilities.Earth.EarthSmash.ShootRange");
		this.damage = getConfig().getDouble("Abilities.Earth.EarthSmash.Damage");
		this.knockback = getConfig().getDouble("Abilities.Earth.EarthSmash.Knockback");
		this.knockup = getConfig().getDouble("Abilities.Earth.EarthSmash.Knockup");
		this.flightSpeed = getConfig().getDouble("Abilities.Earth.EarthSmash.FlightSpeed");
		this.chargeTime = getConfig().getLong("Abilities.Earth.EarthSmash.ChargeTime");
		this.cooldown = getConfig().getLong("Abilities.Earth.EarthSmash.Cooldown");
		this.flightRemoveTimer = getConfig().getLong("Abilities.Earth.EarthSmash.FlightTimer");
		this.removeTimer = getConfig().getLong("Abilities.Earth.EarthSmash.RemoveTimer");
		this.affectedEntities = new ArrayList<>();
		this.currentBlocks = new ArrayList<>();
		this.affectedBlocks = new ArrayList<>();
		
		if (type == ClickType.SHIFT_DOWN || type == ClickType.SHIFT_UP && !player.isSneaking()) {
			if (bPlayer.isAvatarState()) {
				selectRange = AvatarState.getValue(selectRange);
				grabRange = AvatarState.getValue(grabRange);
				chargeTime = 0;
				cooldown = 0;
				damage = AvatarState.getValue(damage);
				knockback = AvatarState.getValue(knockback);
				knockup = AvatarState.getValue(knockup);
				flightSpeed = AvatarState.getValue(flightSpeed);
				flightRemoveTimer = Integer.MAX_VALUE;
				shootRange = AvatarState.getValue(shootRange);
			}

			EarthSmash flySmash = flyingInSmashCheck(player);
			if (flySmash != null) {
				flySmash.state = State.FLYING;
				flySmash.player = player;
				flySmash.flightStartTime = System.currentTimeMillis();
				return;
			}

			EarthSmash grabbedSmash = aimingAtSmashCheck(player, State.LIFTED);
			if (grabbedSmash == null) {
				if (bPlayer.isOnCooldown(this)) {
					return;
				}
				grabbedSmash = aimingAtSmashCheck(player, State.SHOT);
			}
			
			if (grabbedSmash != null) {
				grabbedSmash.state = State.GRABBED;
				grabbedSmash.grabbedDistance = grabbedSmash.location.distance(player.getEyeLocation());
				grabbedSmash.player = player;
				return;
			}
			
			start();
		} else if (type == ClickType.LEFT_CLICK && player.isSneaking()) {
			for (EarthSmash smash : getAbilities(EarthSmash.class)) {
				if (smash.state == State.GRABBED && smash.player == player) {
					smash.state = State.SHOT;
					smash.destination = player.getEyeLocation().clone().add(player.getEyeLocation().getDirection().normalize().multiply(smash.shootRange));
					smash.location.getWorld().playEffect(smash.location, Effect.GHAST_SHOOT, 0, 10);
				}
			}
			return;
		} else if (type == ClickType.RIGHT_CLICK && player.isSneaking()) {
			EarthSmash grabbedSmash = aimingAtSmashCheck(player, State.GRABBED);
			if (grabbedSmash != null) {
				player.teleport(grabbedSmash.location.clone().add(0, 2, 0));
				grabbedSmash.state = State.FLYING;
				grabbedSmash.player = player;
				grabbedSmash.flightStartTime = System.currentTimeMillis();
			}
			return;
		}
	}

	@Override
	public void progress() {
		progressCounter++;
		if (state == State.LIFTED && removeTimer > 0 && System.currentTimeMillis() - startTime > removeTimer) {
			remove();
			return;
		}
		
		if (state == State.START) {
			if (!bPlayer.canBend(this)) {
				remove();
				return;
			}
		} else if (state == State.START || state == State.FLYING || state == State.GRABBED) {
			if (!bPlayer.canBendIgnoreCooldowns(this)) {
				remove();
				return;
			}
		}

		if (state == State.START && progressCounter > 1) {
			if (!player.isSneaking()) {
				if (System.currentTimeMillis() - startTime >= chargeTime) {
					origin = getEarthSourceBlock(selectRange);
					if (origin == null) {
						remove();
						return;
					}
					bPlayer.addCooldown(this);
					location = origin.getLocation();
					state = State.LIFTING;
				} else {
					remove();
					return;
				}
			} else if (System.currentTimeMillis() - startTime > chargeTime) {
				Location tempLoc = player.getEyeLocation().add(player.getEyeLocation().getDirection().normalize().multiply(1.2));
				tempLoc.add(0, 0.3, 0);
				ParticleEffect.SMOKE.display(tempLoc, 0.3F, 0.1F, 0.3F, 0, 4);
			}
		} else if (state == State.LIFTING) {
			if (System.currentTimeMillis() - delay >= liftAnimationInterval) {
				delay = System.currentTimeMillis();
				animateLift();
			}
		} else if (state == State.GRABBED) {
			if (player.isSneaking()) {
				revert();
				Location oldLoc = location.clone();
				location = player.getEyeLocation().add(player.getEyeLocation().getDirection().normalize().multiply(grabbedDistance));

				// Check to make sure the new location is available to move to
				for (Block block : getBlocks()) {
					if (block.getType() != Material.AIR && !isTransparent(block)) {
						location = oldLoc;
						break;
					}
				}
				
				WaterAbility.removeWaterSpouts(location, 2, player);
				AirAbility.removeAirSpouts(location, 2, player);
				draw();
				return;
			} else {
				state = State.LIFTED;
				return;
			}
		} else if (state == State.SHOT) {
			if (System.currentTimeMillis() - delay >= shootAnimationInterval) {
				delay = System.currentTimeMillis();
				if (GeneralMethods.isRegionProtectedFromBuild(this, location)) {
					remove();
					return;
				}
				
				revert();
				location.add(GeneralMethods.getDirection(location, destination).normalize().multiply(1));
				if (location.distanceSquared(destination) < 4) {
					remove();
					return;
				}
				
				// If an earthsmash runs into too many blocks we should remove it
				int badBlocksFound = 0;
				for (Block block : getBlocks()) {
					if (block.getType() != Material.AIR && (!isTransparent(block) 
							|| block.getType() == Material.WATER 
							|| block.getType() == Material.STATIONARY_WATER)) {
						badBlocksFound++;
					}
				}

				if (badBlocksFound > maxBlocksToPassThrough) {
					remove();
					return;
				}
				WaterAbility.removeWaterSpouts(location, 2, player);
				AirAbility.removeAirSpouts(location, 2, player);
				shootingCollisionDetection();
				draw();
				smashToSmashCollisionDetection();
			}
			return;
		} else if (state == State.FLYING) {
			if (!player.isSneaking()) {
				remove();
				return;
			} else if (System.currentTimeMillis() - delay >= flightAnimationInterval) {
				delay = System.currentTimeMillis();
				if (GeneralMethods.isRegionProtectedFromBuild(this, location)) {
					remove();
					return;
				}
				revert();
				destination = player.getEyeLocation().clone().add(player.getEyeLocation().getDirection().normalize().multiply(shootRange));
				Vector direction = GeneralMethods.getDirection(location, destination).normalize();

				List<Entity> entities = GeneralMethods.getEntitiesAroundPoint(location.clone().add(0, 2, 0), flightDetectionRadius);
				if (entities.size() == 0) {
					remove();
					return;
				}
				for (Entity entity : entities) {
					entity.setVelocity(direction.clone().multiply(flightSpeed));
				}

				// These values tend to work well when dealing with a person aiming upward or downward.
				if (direction.getY() < -0.35) {
					location = player.getLocation().clone().add(0, -3.2, 0);
				} else if (direction.getY() > 0.35) {
					location = player.getLocation().clone().add(0, -1.7, 0);
				} else {
					location = player.getLocation().clone().add(0, -2.2, 0);
				}
				draw();
			}
			if (System.currentTimeMillis() - flightStartTime > flightRemoveTimer) {
				remove();
				return;
			}
		}
	}

	/**
	 * Begins animating the EarthSmash from the ground. The lift animation
	 * consists of 3 steps, and each one has to design the shape in the
	 * ground that removes the Earthbendable material. We also need to make
	 * sure that there is a clear path for the EarthSmash to rise, and that
	 * there is enough Earthbendable material for it to be created.
	 */
	@SuppressWarnings("deprecation")
	public void animateLift() {
		if (animationCounter < 4) {
			revert();
			location.add(0, 1, 0);
			//Remove the blocks underneath the rising smash
			if (animationCounter == 0) {
				//Check all of the blocks and make sure that they can be removed AND make sure there is enough dirt
				int totalBendableBlocks = 0;
				for (int x = -1; x <= 1; x++) {
					for (int y = -2; y <= -1; y++) {
						for (int z = -1; z <= 1; z++) {
							Block block = location.clone().add(x, y, z).getBlock();
							if (GeneralMethods.isRegionProtectedFromBuild(this, block.getLocation())) {
								remove();
								return;
							}
							if (isEarthbendable(block)) {
								totalBendableBlocks++;
							}
						}
					}
				}
				if (totalBendableBlocks < requiredBendableBlocks) {
					remove();
					return;
				}
				//Make sure there is a clear path upward otherwise remove
				for (int y = 0; y <= 3; y++) {
					Block tempBlock = location.clone().add(0, y, 0).getBlock();
					if (!isTransparent(tempBlock) && tempBlock.getType() != Material.AIR) {
						remove();
						return;
					}
				}
				//Design what this EarthSmash looks like by using BlockRepresenters
				Location tempLoc = location.clone().add(0, -2, 0);
				for (int x = -1; x <= 1; x++) {
					for (int y = -1; y <= 1; y++) {
						for (int z = -1; z <= 1; z++) {
							if ((Math.abs(x) + Math.abs(y) + Math.abs(z)) % 2 == 0) {
								Block block = tempLoc.clone().add(x, y, z).getBlock();
								currentBlocks.add(new BlockRepresenter(x, y, z, selectMaterialForRepresenter(block.getType()), block.getData()));
							}
						}
					}
				}

				//Remove the design of the second level of removed dirt
				for (int x = -1; x <= 1; x++) {
					for (int z = -1; z <= 1; z++) {
						if ((Math.abs(x) + Math.abs(z)) % 2 == 1) {
							Block block = location.clone().add(x, -2, z).getBlock();
							if (isEarthbendable(block)) {
								addTempAirBlock(block);
							}
						}

						//Remove the first level of dirt
						Block block = location.clone().add(x, -1, z).getBlock();
						if (isEarthbendable(block)) {
							addTempAirBlock(block);
						}
					}
				}
				
				/*
				 * We needed to calculate all of the blocks based on the
				 * location being 1 above the initial bending block, however we
				 * want to animate it starting from the original bending block.
				 * We must readjust the location back to what it originally was.
				 */
				location.add(0, -1, 0);

			}
			//Move any entities that are above the rock
			List<Entity> entities = GeneralMethods.getEntitiesAroundPoint(location, 2.5);
			for (Entity entity : entities) {
				org.bukkit.util.Vector velocity = entity.getVelocity();
				entity.setVelocity(velocity.add(new Vector(0, 0.36, 0)));
			}
			location.getWorld().playEffect(location, Effect.GHAST_SHOOT, 0, 7);
			draw();
		} else {
			state = State.LIFTED;
		}
		animationCounter++;
	}

	/**
	 * Redraws the blocks for this instance of EarthSmash.
	 */
	public void draw() {
		if (currentBlocks.size() == 0) {
			remove();
			return;
		}
		for (BlockRepresenter blockRep : currentBlocks) {
			Block block = location.clone().add(blockRep.getX(), blockRep.getY(), blockRep.getZ()).getBlock();
			if (block.getType().equals(Material.SAND) || block.getType().equals(Material.GRAVEL)) { //Check if block can be affected by gravity.
				addTempAirBlock(block); //If so, set it to a temp air block.
			}
			if (player != null && isTransparent(block)) {
				affectedBlocks.add(new TempBlock(block, blockRep.getType(), blockRep.getData()));
				getPreventEarthbendingBlocks().add(block);
			}
		}
	}

	public void revert() {
		checkRemainingBlocks();
		for (int i = 0; i < affectedBlocks.size(); i++) {
			TempBlock tblock = affectedBlocks.get(i);
			getPreventEarthbendingBlocks().remove(tblock.getBlock());
			tblock.revertBlock();
			affectedBlocks.remove(i);
			i--;
		}
	}

	/**
	 * Checks to see which of the blocks are still attached to the
	 * EarthSmash, remember that blocks can be broken or used in other
	 * abilities so we need to double check and remove any that are not
	 * still attached.
	 * 
	 * Also when we remove the blocks from instances, movedearth, or tempair
	 * we should do it on a delay because tempair takes a couple seconds
	 * before the block shows up in that map.
	 */
	public void checkRemainingBlocks() {
		for (int i = 0; i < currentBlocks.size(); i++) {
			BlockRepresenter brep = currentBlocks.get(i);
			final Block block = location.clone().add(brep.getX(), brep.getY(), brep.getZ()).getBlock();
			// Check for grass because sometimes the dirt turns into grass.
			if (block.getType() != brep.getType() && (block.getType() != Material.GRASS) && (block.getType() != Material.COBBLESTONE)) {
				currentBlocks.remove(i);
				i--;
			}
		}
	}

	public void remove() {
		super.remove();
		state = State.REMOVED;
		revert();
	}

	/**
	 * Gets the blocks surrounding the EarthSmash's loc. This method ignores
	 * the blocks that should be Air, and only returns the ones that are
	 * dirt.
	 */
	public List<Block> getBlocks() {
		List<Block> blocks = new ArrayList<Block>();
		for (int x = -1; x <= 1; x++) {
			for (int y = -1; y <= 1; y++) {
				for (int z = -1; z <= 1; z++) {
					if ((Math.abs(x) + Math.abs(y) + Math.abs(z)) % 2 == 0) { //Give it the cool shape
						if (location != null) {
							blocks.add(location.getWorld().getBlockAt(location.clone().add(x, y, z)));
						}
					}
				}
			}
		}
		return blocks;
	}

	/**
	 * Gets the blocks surrounding the EarthSmash's loc. This method returns
	 * all the blocks surrounding the loc, including dirt and air.
	 */
	public List<Block> getBlocksIncludingInner() {
		List<Block> blocks = new ArrayList<Block>();
		for (int x = -1; x <= 1; x++) {
			for (int y = -1; y <= 1; y++) {
				for (int z = -1; z <= 1; z++) {
					if (location != null) {
						blocks.add(location.getWorld().getBlockAt(location.clone().add(x, y, z)));
					}
				}
			}
		}
		return blocks;
	}
	
	/**
	 * Switches the Sand Material and Gravel to SandStone and stone
	 * respectively, since gravel and sand cannot be bent due to gravity.
	 */
	public static Material selectMaterial(Material mat) {
		if (mat == Material.SAND) {
			return Material.SANDSTONE;
		} else if (mat == Material.GRAVEL) {
			return Material.STONE;
		} else {
			return mat;
		}
	}

	public Material selectMaterialForRepresenter(Material mat) {
		Material tempMat = selectMaterial(mat);
		Random rand = new Random();
		if (!isEarthbendable(tempMat) && !isMetalbendable(tempMat)) {
			if (currentBlocks.size() < 1) {
				return Material.DIRT;
			} else {
				return currentBlocks.get(rand.nextInt(currentBlocks.size())).getType();
			}
		}
		return tempMat;
	}
	
	/**
	 * Determines if a player is trying to grab an EarthSmash. A player is
	 * trying to grab an EarthSmash if they are staring at it and holding
	 * shift.
	 */
	private EarthSmash aimingAtSmashCheck(Player player, State reqState) {
		if (!allowGrab) {
			return null;
		}
		
		List<Block> blocks = GeneralMethods.getBlocksAroundPoint(GeneralMethods.getTargetedLocation(player, grabRange, GeneralMethods.NON_OPAQUE), 1);
		for (EarthSmash smash : getAbilities(EarthSmash.class)) {
			if (reqState == null || smash.state == reqState) {
				for (Block block : blocks) {
					if (block == null || smash.getLocation() == null) {
						continue;
					}
					if (block.getLocation().getWorld() == smash.location.getWorld() 
							&& block.getLocation().distanceSquared(smash.location) <= Math.pow(grabDetectionRadius, 2)) {
						return smash;
					}
				}
			}
		}
		return null;
	}

	/**
	 * This method handles any collision between an EarthSmash and the
	 * surrounding entities, the method only applies to earthsmashes that
	 * have already been shot.
	 */
	public void shootingCollisionDetection() {
		List<Entity> entities = GeneralMethods.getEntitiesAroundPoint(location, flightDetectionRadius);
		for (Entity entity : entities) {
			if (entity instanceof LivingEntity && entity != player && !affectedEntities.contains(entity)) {
				affectedEntities.add(entity);
				double damage = currentBlocks.size() / 13.0 * this.damage;
				GeneralMethods.damageEntity(this, entity, damage);
				Vector travelVec = GeneralMethods.getDirection(location, entity.getLocation());
				entity.setVelocity(travelVec.setY(knockup).normalize().multiply(knockback));
			}
		}
	}
	
	/**
	 * EarthSmash to EarthSmash collision can only happen when one of the
	 * Smashes have been shot by a player. If we find out that one of them
	 * have collided then we want to return since a smash can only remove 1
	 * at a time.
	 */
	public void smashToSmashCollisionDetection() {
		for (EarthSmash smash : getAbilities(EarthSmash.class)) {
			if (smash.location != null && smash != this && smash.location.getWorld() == location.getWorld() 
					&& smash.location.distanceSquared(location) < Math.pow(flightDetectionRadius, 2)) {
				smash.remove();
				remove();
				return;
			}
		}
	}
	
	/**
	 * Determines whether or not a player is trying to fly ontop of an
	 * EarthSmash. A player is considered "flying" if they are standing
	 * ontop of the earthsmash and holding shift.
	 */
	private static EarthSmash flyingInSmashCheck(Player player) {
		for (EarthSmash smash : getAbilities(EarthSmash.class)) {
			if (!smash.allowFlight) {
				continue;
			}
			//Check to see if the player is standing on top of the smash.
			if (smash.state == State.LIFTED) {
				if (smash.location.getWorld().equals(player.getWorld()) 
						&& smash.location.clone().add(0, 2, 0).distanceSquared(player.getLocation()) <= Math.pow(smash.flightDetectionRadius, 2)) {
					return smash;
				}
			}
		}
		return null;
	}

	/**
	 * A BlockRepresenter is used to keep track of each of the individual
	 * types of blocks that are attached to an EarthSmash. Without the
	 * representer then an EarthSmash can only be made up of 1 material at a
	 * time. For example, an ESmash that is entirely dirt, coalore, or
	 * sandstone. Using the representer will allow all the materials to be
	 * mixed together.
	 */
	public class BlockRepresenter {
		private int x, y, z;
		private Material type;
		private byte data;

		public BlockRepresenter(int x, int y, int z, Material type, byte data) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.type = type;
			this.data = data;
		}

		public int getX() {
			return x;
		}

		public int getY() {
			return y;
		}

		public int getZ() {
			return z;
		}

		public Material getType() {
			return type;
		}

		public byte getData() {
			return data;
		}

		public void setX(int x) {
			this.x = x;
		}

		public void setY(int y) {
			this.y = y;
		}

		public void setZ(int z) {
			this.z = z;
		}

		public void setType(Material type) {
			this.type = type;
		}

		public void setData(byte data) {
			this.data = data;
		}

		public String toString() {
			return x + ", " + y + ", " + z + ", " + type.toString();
		}
	}

	public class Pair<F, S> {
		private F first; //first member of pair
		private S second; //second member of pair

		public Pair(F first, S second) {
			this.first = first;
			this.second = second;
		}

		public void setFirst(F first) {
			this.first = first;
		}

		public void setSecond(S second) {
			this.second = second;
		}

		public F getFirst() {
			return first;
		}

		public S getSecond() {
			return second;
		}
	}

	@Override
	public String getName() {
		return "EarthSmash";
	}

	@Override
	public Location getLocation() {
		return location;
	}

	@Override
	public long getCooldown() {
		return cooldown;
	}
	
	@Override
	public boolean isSneakAbility() {
		return true;
	}

	@Override
	public boolean isHarmlessAbility() {
		return false;
	}

	public boolean isAllowGrab() {
		return allowGrab;
	}

	public void setAllowGrab(boolean allowGrab) {
		this.allowGrab = allowGrab;
	}

	public boolean isAllowFlight() {
		return allowFlight;
	}

	public void setAllowFlight(boolean allowFlight) {
		this.allowFlight = allowFlight;
	}

	public int getAnimationCounter() {
		return animationCounter;
	}

	public void setAnimationCounter(int animationCounter) {
		this.animationCounter = animationCounter;
	}

	public int getProgressCounter() {
		return progressCounter;
	}

	public void setProgressCounter(int progressCounter) {
		this.progressCounter = progressCounter;
	}

	public int getRequiredBendableBlocks() {
		return requiredBendableBlocks;
	}

	public void setRequiredBendableBlocks(int requiredBendableBlocks) {
		this.requiredBendableBlocks = requiredBendableBlocks;
	}

	public int getMaxBlocksToPassThrough() {
		return maxBlocksToPassThrough;
	}

	public void setMaxBlocksToPassThrough(int maxBlocksToPassThrough) {
		this.maxBlocksToPassThrough = maxBlocksToPassThrough;
	}

	public long getDelay() {
		return delay;
	}

	public void setDelay(long delay) {
		this.delay = delay;
	}

	public long getChargeTime() {
		return chargeTime;
	}

	public void setChargeTime(long chargeTime) {
		this.chargeTime = chargeTime;
	}

	public long getRemoveTimer() {
		return removeTimer;
	}

	public void setRemoveTimer(long removeTimer) {
		this.removeTimer = removeTimer;
	}

	public long getFlightRemoveTimer() {
		return flightRemoveTimer;
	}

	public void setFlightRemoveTimer(long flightRemoveTimer) {
		this.flightRemoveTimer = flightRemoveTimer;
	}

	public long getFlightStartTime() {
		return flightStartTime;
	}

	public void setFlightStartTime(long flightStartTime) {
		this.flightStartTime = flightStartTime;
	}

	public long getShootAnimationInterval() {
		return shootAnimationInterval;
	}

	public void setShootAnimationInterval(long shootAnimationInterval) {
		this.shootAnimationInterval = shootAnimationInterval;
	}

	public long getFlightAnimationInterval() {
		return flightAnimationInterval;
	}

	public void setFlightAnimationInterval(long flightAnimationInterval) {
		this.flightAnimationInterval = flightAnimationInterval;
	}

	public long getLiftAnimationInterval() {
		return liftAnimationInterval;
	}

	public void setLiftAnimationInterval(long liftAnimationInterval) {
		this.liftAnimationInterval = liftAnimationInterval;
	}

	public double getGrabRange() {
		return grabRange;
	}

	public void setGrabRange(double grabRange) {
		this.grabRange = grabRange;
	}
	
	public double getSelectRange() {
		return selectRange;
	}

	public void setSelectRange(double selectRange) {
		this.selectRange = selectRange;
	}

	public double getShootRange() {
		return shootRange;
	}

	public void setShootRange(double shootRange) {
		this.shootRange = shootRange;
	}

	public double getDamage() {
		return damage;
	}

	public void setDamage(double damage) {
		this.damage = damage;
	}

	public double getKnockback() {
		return knockback;
	}

	public void setKnockback(double knockback) {
		this.knockback = knockback;
	}

	public double getKnockup() {
		return knockup;
	}

	public void setKnockup(double knockup) {
		this.knockup = knockup;
	}

	public double getFlightSpeed() {
		return flightSpeed;
	}

	public void setFlightSpeed(double flightSpeed) {
		this.flightSpeed = flightSpeed;
	}

	public double getGrabbedDistance() {
		return grabbedDistance;
	}

	public void setGrabbedDistance(double grabbedDistance) {
		this.grabbedDistance = grabbedDistance;
	}

	public double getGrabDetectionRadius() {
		return grabDetectionRadius;
	}

	public void setGrabDetectionRadius(double grabDetectionRadius) {
		this.grabDetectionRadius = grabDetectionRadius;
	}

	public double getFlightDetectionRadius() {
		return flightDetectionRadius;
	}

	public void setFlightDetectionRadius(double flightDetectionRadius) {
		this.flightDetectionRadius = flightDetectionRadius;
	}

	public State getState() {
		return state;
	}

	public void setState(State state) {
		this.state = state;
	}

	public Block getOrigin() {
		return origin;
	}

	public void setOrigin(Block origin) {
		this.origin = origin;
	}

	public Location getDestination() {
		return destination;
	}

	public void setDestination(Location destination) {
		this.destination = destination;
	}

	public ArrayList<Entity> getAffectedEntities() {
		return affectedEntities;
	}

	public ArrayList<BlockRepresenter> getCurrentBlocks() {
		return currentBlocks;
	}

	public ArrayList<TempBlock> getAffectedBlocks() {
		return affectedBlocks;
	}

	public void setCooldown(long cooldown) {
		this.cooldown = cooldown;
	}

	public void setLocation(Location location) {
		this.location = location;
	}

}
