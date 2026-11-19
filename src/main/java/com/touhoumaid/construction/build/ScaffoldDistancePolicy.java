package com.touhoumaid.construction.build;

/**
 * Vanilla {@code ScaffoldingBlock.getDistance} only counts {@code Blocks.SCAFFOLDING}. Construction
 * staging towers use a separate block, so distance must treat any walkable scaffold the same.
 */
public final class ScaffoldDistancePolicy {
    public static final int NO_SCAFFOLD = -1;
    public static final int[] NO_HORIZONTAL = {NO_SCAFFOLD, NO_SCAFFOLD, NO_SCAFFOLD, NO_SCAFFOLD};

    public sealed interface Below permits Below.Sturdy, Below.None, Below.Scaffold {
        record Sturdy() implements Below {
        }

        record None() implements Below {
        }

        record Scaffold(int distance) implements Below {
        }

        Below STURDY = new Sturdy();
        Below NONE = new None();

        static Below scaffold(int distance) {
            return new Scaffold(distance);
        }
    }

    private ScaffoldDistancePolicy() {
    }

    /**
     * @param horizontalNeighborDistances length 4; {@link #NO_SCAFFOLD} or that neighbor's DISTANCE
     */
    public static int compute(Below below, int[] horizontalNeighborDistances) {
        int distance;
        if (below instanceof Below.Scaffold scaffold) {
            distance = scaffold.distance();
        } else if (below instanceof Below.Sturdy) {
            return 0;
        } else {
            distance = 7;
        }
        if (horizontalNeighborDistances != null) {
            for (int neighbor : horizontalNeighborDistances) {
                if (neighbor < 0) {
                    continue;
                }
                distance = Math.min(distance, neighbor + 1);
                if (distance == 1) {
                    break;
                }
            }
        }
        return distance;
    }
}
