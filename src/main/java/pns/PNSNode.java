// # Adapted from the github.com/ Ludii
// search/pns/
// with modifications
package pns;

import java.util.Arrays;
import main.collections.FastArrayList;
import other.context.Context;
import other.move.Move;

public class PNSNode {
    public enum TYPE {
        OR,
        AND
    }

    public enum VALUE {
        TRUE,
        FALSE,
        UNKNOWN
    }

    protected final PNSNode parent;
    protected final TYPE type;
    protected final Context context;
    protected final PNSNode[] children;
    protected final Move[] legalMoves;
    private boolean isExpanded = false;
    private int proofNumber = -1, disproofNumber = -1;
    private VALUE value = VALUE.UNKNOWN;

    public PNSNode(final PNSNode parent, final Context context, final int proofPlayer) {
        this.parent = parent;
        this.context = context;

        final var mover = context.state().mover();

        if (mover == proofPlayer) {
            type = TYPE.OR;
        } else {
            type = TYPE.AND;
        }

        if (context.trial().over()) {
            // empty list of actions
            legalMoves = new Move[0];
        } else {
            final FastArrayList<Move> actions = context.game().moves(context).moves();
            legalMoves = new Move[actions.size()];
            actions.toArray(legalMoves);
        }

        children = new PNSNode[legalMoves.length];
    }

    public PNSNode[] children() {
        return children;
    }

    public Context context() {
        return context;
    }

    public void deleteSubtree() {
        Arrays.fill(children, null);
    }

    public int disproofNumber() {
        assert (disproofNumber >= 0);
        return disproofNumber;
    }

    public int proofNumber() {
        assert (proofNumber >= 0);
        return proofNumber;
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    public TYPE type() {
        return type;
    }

    public void setDisproofNumber(final int disproofNumber) {
        this.disproofNumber = disproofNumber;
    }

    public void setExpanded(final boolean isExpanded) {
        this.isExpanded = isExpanded;
    }

    public void setProofNumber(final int proofNumber) {
        this.proofNumber = proofNumber;
    }

    public void setValue(final VALUE value) {
        this.value = value;
    }

    public VALUE value() {
        return value;
    }
}
