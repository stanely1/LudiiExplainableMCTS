// # Adapted from the github.com/ Ludii
// search/pns/
// with modifications

package pns;

import game.Game;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import other.AI;
import other.context.Context;
import other.move.Move;
import pns.PNSNode.TYPE;
import pns.PNSNode.VALUE;

public class ProofNumberSearch extends AI {
    protected int proofPlayer = -1;
    protected double bestPossibleRank = -1.0;
    protected double worstPossibleRank = -1.0;

    public ProofNumberSearch() {
        friendlyName = "Proof-Number Search";
    }

    @Override
    public Move selectAction(
            final Game game,
            final Context context,
            final double maxSeconds,
            final int maxIterations,
            final int maxDepth) {

        bestPossibleRank = context.computeNextWinRank();
        worstPossibleRank = context.computeNextLossRank();

        if (proofPlayer != context.state().mover()) {
            System.err.println("Warning: Current mover = " + context.state().mover() + ", but proof player = "
                    + proofPlayer + "!");
        }

        final PNSNode root = new PNSNode(null, copyContext(context), proofPlayer);
        eval(root);
        setNumbers(root);

        PNSNode current = root;

        // ------------------------------------------------------------------------------------------------------------
        final long stopTime =
                (maxSeconds > 0.0) ? System.currentTimeMillis() + (long) (maxSeconds * 1000L) : Long.MAX_VALUE;
        final int maxIts = (maxIterations >= 0) ? maxIterations : Integer.MAX_VALUE;
        int numIterations = 0;
        // ------------------------------------------------------------------------------------------------------------

        while (numIterations < maxIts
                && System.currentTimeMillis() < stopTime
                && !wantsInterrupt
                && root.proofNumber() != 0
                && root.disproofNumber() != 0) {

            final PNSNode mostProvingNode = selectMostProvingNode(current);
            expandNode(mostProvingNode);
            current = updateAncestors(mostProvingNode);
        }

        if (root.proofNumber() == 0) System.out.println("Proved a win!");
        else System.out.println("Disproved a win!");

        int index = IntStream.range(0, root.children.length)
                .filter(i -> root.children[i].proofNumber() == 0)
                .findFirst()
                .orElse(ThreadLocalRandom.current().nextInt(root.legalMoves.length));

        return root.legalMoves[index];
    }

    private void eval(final PNSNode node) {
        final Context ctx = node.context();
        if (ctx.trial().over()) {
            final double rank = ctx.trial().ranking()[proofPlayer];
            if (rank == bestPossibleRank) {
                node.setValue(VALUE.TRUE);
            } else {
                node.setValue(VALUE.FALSE);
            }
        } else {
            node.setValue(VALUE.UNKNOWN);
        }
    }

    private static void setNumbers(final PNSNode node) {
        if (node.isExpanded()) // internal node
        {
            if (node.type() == TYPE.AND) {
                node.setProofNumber(0);
                node.setDisproofNumber(Integer.MAX_VALUE);

                for (final PNSNode child : node.children()) {
                    if (node.proofNumber() == Integer.MAX_VALUE || child.proofNumber() == Integer.MAX_VALUE)
                        node.setProofNumber(Integer.MAX_VALUE);
                    else node.setProofNumber(node.proofNumber() + child.proofNumber());

                    if (child != null && child.disproofNumber() < node.disproofNumber())
                        node.setDisproofNumber(child.disproofNumber());
                }
            } else // OR node
            {
                node.setProofNumber(Integer.MAX_VALUE);
                node.setDisproofNumber(0);

                for (final PNSNode child : node.children()) {
                    if (node.disproofNumber() == Integer.MAX_VALUE || child.disproofNumber() == Integer.MAX_VALUE)
                        node.setDisproofNumber(Integer.MAX_VALUE);
                    else node.setDisproofNumber(node.disproofNumber() + child.disproofNumber());

                    if (child != null && child.proofNumber() < node.proofNumber())
                        node.setProofNumber(child.proofNumber());
                }
            }
        } else // leaf node
        {
            switch (node.value()) {
                case FALSE:
                    node.setProofNumber(Integer.MAX_VALUE);
                    node.setDisproofNumber(0);
                    break;
                case TRUE:
                    node.setProofNumber(0);
                    node.setDisproofNumber(Integer.MAX_VALUE);
                    break;
                case UNKNOWN:
                    if (node.type() == TYPE.AND) {
                        node.setProofNumber(Math.max(1, node.children.length));
                        node.setDisproofNumber(1);
                    } else // OR node
                    {
                        node.setProofNumber(1);
                        node.setDisproofNumber(Math.max(1, node.children.length));
                    }

                    break;
            }
        }
    }

    private static PNSNode selectMostProvingNode(PNSNode node) {
        while (node.isExpanded()) {
            final PNSNode[] children = node.children();
            final var temp_node = node;

            node = Arrays.stream(children)
                    .filter(c -> (temp_node.type() == TYPE.OR
                            ? c.proofNumber() == temp_node.proofNumber()
                            : c.disproofNumber() == temp_node.disproofNumber()))
                    .findFirst()
                    .orElse(children[0]);
        }
        return node;
    }

    private void expandNode(final PNSNode node) {
        final PNSNode[] children = node.children();

        for (int i = 0; i < children.length; ++i) {
            final Context newContext = new Context(node.context());
            newContext.game().apply(newContext, node.legalMoves[i]);
            final PNSNode child = new PNSNode(node, newContext, proofPlayer);
            children[i] = child;

            eval(child);
            setNumbers(child);

            if ((node.type() == TYPE.OR && child.proofNumber() == 0)
                    || (node.type() == TYPE.AND && child.disproofNumber() == 0)) {
                break;
            }
        }

        node.setExpanded(true);
    }

    private static PNSNode updateAncestors(final PNSNode inNode) {
        PNSNode node = inNode;

        do {
            final int oldProof = node.proofNumber();
            final int oldDisproof = node.disproofNumber();

            setNumbers(node);

            if (node.proofNumber() == oldProof && node.disproofNumber() == oldDisproof) {
                return node;
            }

            // Delete (dis)proved subtrees
            if (node.proofNumber() == 0 || node.disproofNumber() == 0) node.deleteSubtree();

            if (node.parent == null) return node;

            node = node.parent;
        } while (true);
    }
    // ------------------------------------------------------------------------------------------------------------

    @Override
    public void initAI(final Game game, final int playerID) {
        proofPlayer = playerID;
    }

    @Override
    public boolean supportsGame(final Game game) {
        if (game.players().count() != 2) return false;

        if (game.isStochasticGame()) return false;

        if (game.hiddenInformation()) return false;

        return game.isAlternatingMoveGame();
    }
}
