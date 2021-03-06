package org.apollo.game.model.area.collision;

import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import org.apollo.cache.def.ObjectDefinition;
import org.apollo.game.model.Direction;
import org.apollo.game.model.Position;
import org.apollo.game.model.entity.obj.GameObject;
import org.apollo.game.model.entity.obj.ObjectType;

import java.util.stream.Stream;

/**
 * A global update to the collision matrices.
 */
public final class CollisionUpdate {
	/**
	 * The type of this update.
	 */
	private final CollisionUpdateType type;

	/**
	 * A mapping of {@link Position}s to a set of their {@link DirectionFlag}s.
	 */
	private final Multimap<Position, DirectionFlag> flags;

	public CollisionUpdate(CollisionUpdateType type, Multimap<Position, DirectionFlag> flags) {
		this.type = type;
		this.flags = flags;
	}

	/**
	 * Get the type of this update (ADDING, or REMOVING).
	 *
	 * @return The type of this update.
	 */
	public CollisionUpdateType getType() {
		return type;
	}

	/**
	 * Get the mapping of tiles -> flags contained in this update.
	 *
	 * @return The flags contained in this update.
	 */
	public Multimap<Position, DirectionFlag> getFlags() {
		return flags;
	}

	/**
	 * A directional flag in a {@code CollisionUpdate}.  Consists of a {@code direction} and a flag indicating whether
	 * that tile is impenetrable as well as untraversable.
	 */
	public static final class DirectionFlag {
		private final boolean impenetrable;
		private final Direction direction;

		public DirectionFlag(boolean impenetrable, Direction direction) {
			this.impenetrable = impenetrable;
			this.direction = direction;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			DirectionFlag that = (DirectionFlag) o;

			if (impenetrable != that.impenetrable) return false;
			return direction == that.direction;

		}

		@Override
		public int hashCode() {
			int result = (impenetrable ? 1 : 0);
			result = 31 * result + direction.hashCode();
			return result;
		}

		/**
		 * Check if this flag represents an impenetrable direction.
		 *
		 * @return {@code true} if this flag represents an impenetrable direction, {@code false} otherwise.
		 */
		public boolean isImpenetrable() {
			return impenetrable;
		}

		/**
		 * Get the direction this flag represents.
		 *
		 * @return The direction this flag represents.
		 */
		public Direction getDirection() {
			return direction;
		}
	}

	public static class Builder {
		private final Multimap<Position, DirectionFlag> flags;
		private CollisionUpdateType type;

		public Builder() {
			this.flags = MultimapBuilder.hashKeys().hashSetValues().build();
		}

		/**
		 * Set the type of the {@link CollisionUpdate}.  Can only be called once.
		 *
		 * @param type The type of collision update to use.
		 */
		public void type(CollisionUpdateType type) {
			Preconditions.checkState(type != null, "update type has already been set");
			this.type = type;
		}

		/**
		 * Sets the tile at the given {@code position} as untraversable in the given directions.
		 *
		 * @param position The world position of the tile.
		 * @param directions The directions that are untraversable from this tile.
		 */
		public void tile(Position position, boolean impenetrable, Direction... directions) {
			if (directions.length == 0) {
				return;
			}

			Stream.of(directions).forEach(direction -> flags.put(position, new DirectionFlag(impenetrable, direction)));
		}

		/**
		 * Flag a wall in the CollisionUpdate.  When constructing a CollisionMatrix, the flags for a wall are represented
		 * as the tile the wall exists on and the tile one step in the facing direction. So for a wall facing south,
		 * the tile one step to the south be flagged as untraversable from the north.
		 *
		 * @param position The position of the wall.
		 * @param impenetrable If projectiles can pass through this wall.
		 * @param orientation The facing direction of this wall.
		 */
		public void wall(Position position, boolean impenetrable, Direction orientation) {
			tile(position, impenetrable, orientation);
			tile(position.step(1, orientation), impenetrable, orientation.opposite());
		}

		/**
		 * Flag a larger corner wall in the CollisionUpdate.  A corner is represented by the 2 directions that it faces,
		 * and the 2 tiles in both directions.  For example, when a tile is facing north its facing directions
		 * are north and east, so the position of the object will be untraversable from those directions.  Additionally,
		 * the tile 1 step to the north, and 1 step to the east will be untraversable from the opposite directions of
		 * north and east respectively.
		 * <p>
		 * todo: "large corner wall", is that really what this is?
		 *
		 * @param position The position of the corner wall.
		 * @param impenetrable If projectiles can pass through this corner wall.
		 * @param orientation The direction of this corner wall
		 */
		public void largeCornerWall(Position position, boolean impenetrable, Direction orientation) {
			Direction[] directions = Direction.diagonalComponents(orientation);

			tile(position, impenetrable, directions);

			for (Direction direction : directions) {
				tile(position.step(1, direction), impenetrable, direction.opposite());
			}
		}

		/**
		 * Flag a collision update for the given {@link GameObject}.
		 *
		 * @param object The object to update collision flags for.
		 */
		public void object(GameObject object) {
			ObjectDefinition definition = object.getDefinition();
			Position position = object.getPosition();
			int type = object.getType();

			if (!unwalkable(definition, type)) {
				return;
			}

			int x = position.getX(), y = position.getY(), height = position.getHeight();
			int width = definition.getWidth(), length = definition.getLength();
			boolean impenetrable = definition.isImpenetrable();
			int orientation = object.getOrientation();

			// north / south for walls, north east / south west for corners
			if (orientation == 1 || orientation == 3) {
				width = definition.getLength();
				length = definition.getWidth();
			}

			if (type == ObjectType.FLOOR_DECORATION.getValue()) {
				if (definition.isInteractive() && definition.isSolid()) {
					tile(new Position(x, y, height), impenetrable, Direction.NESW);
				}
			} else if (type >= ObjectType.DIAGONAL_WALL.getValue() && type < ObjectType.FLOOR_DECORATION.getValue()) {
				for (int dx = 0; dx < width; dx++) {
					for (int dy = 0; dy < length; dy++) {
						tile(new Position(x + dx, y + dy, height), impenetrable, Direction.NESW);
					}
				}
			} else if (type == ObjectType.LENGTHWISE_WALL.getValue()) {
				wall(position, impenetrable, Direction.WNES[orientation]);
			} else if (type == ObjectType.TRIANGULAR_CORNER.getValue()
				|| type == ObjectType.RECTANGULAR_CORNER.getValue()) {
				wall(position, impenetrable, Direction.WNES_DIAGONAL[orientation]);
			} else if (type == ObjectType.WALL_CORNER.getValue()) {
				largeCornerWall(position, impenetrable, Direction.WNES_DIAGONAL[orientation]);
			}
		}

		/**
		 * Create a new {@link CollisionUpdate}.
		 *
		 * @return A new CollisionUpdate with the flags in this builder.
		 */
		public CollisionUpdate build() {
			Preconditions.checkNotNull(type, "update type must not be null");
			return new CollisionUpdate(type, Multimaps.unmodifiableMultimap(flags));
		}
	}

	/**
	 * Returns whether or not an object with the specified {@link ObjectDefinition} and {@code type} should result in
	 * the tile(s) it is located on being blocked.
	 *
	 * @param definition The {@link ObjectDefinition} of the object.
	 * @param type The type of the object.
	 * @return {@code true} iff the tile(s) the object is on should be blocked.
	 */
	private static boolean unwalkable(ObjectDefinition definition, int type) {
		boolean isSolidFloorDecoration = type == ObjectType.FLOOR_DECORATION.getValue() && definition.isInteractive();

		boolean isWall = type >= ObjectType.LENGTHWISE_WALL.getValue()
			&& type <= ObjectType.RECTANGULAR_CORNER.getValue() || type == ObjectType.DIAGONAL_WALL.getValue();

		boolean isRoof = type > ObjectType.DIAGONAL_INTERACTABLE.getValue()
			&& type < ObjectType.FLOOR_DECORATION.getValue();

		boolean isSolidInteractable = (type == ObjectType.DIAGONAL_INTERACTABLE.getValue()
			|| type == ObjectType.INTERACTABLE.getValue()) && definition.isSolid();

		return isWall || isRoof || isSolidInteractable || isSolidFloorDecoration;
	}
}
