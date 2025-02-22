/* This file was generated by SableCC (http://www.sablecc.org/). */

package prerna.sablecc2.node;

import java.util.*;
import prerna.sablecc2.analysis.*;

@SuppressWarnings("nls")
public final class AMetaRoutine extends PRoutine
{
    private PMetaRoutine _metaRoutine_;
    private final LinkedList<TSemicolon> _semicolon_ = new LinkedList<TSemicolon>();

    public AMetaRoutine()
    {
        // Constructor
    }

    public AMetaRoutine(
        @SuppressWarnings("hiding") PMetaRoutine _metaRoutine_,
        @SuppressWarnings("hiding") List<?> _semicolon_)
    {
        // Constructor
        setMetaRoutine(_metaRoutine_);

        setSemicolon(_semicolon_);

    }

    @Override
    public Object clone()
    {
        return new AMetaRoutine(
            cloneNode(this._metaRoutine_),
            cloneList(this._semicolon_));
    }

    @Override
    public void apply(Switch sw)
    {
        ((Analysis) sw).caseAMetaRoutine(this);
    }

    public PMetaRoutine getMetaRoutine()
    {
        return this._metaRoutine_;
    }

    public void setMetaRoutine(PMetaRoutine node)
    {
        if(this._metaRoutine_ != null)
        {
            this._metaRoutine_.parent(null);
        }

        if(node != null)
        {
            if(node.parent() != null)
            {
                node.parent().removeChild(node);
            }

            node.parent(this);
        }

        this._metaRoutine_ = node;
    }

    public LinkedList<TSemicolon> getSemicolon()
    {
        return this._semicolon_;
    }

    public void setSemicolon(List<?> list)
    {
        for(TSemicolon e : this._semicolon_)
        {
            e.parent(null);
        }
        this._semicolon_.clear();

        for(Object obj_e : list)
        {
            TSemicolon e = (TSemicolon) obj_e;
            if(e.parent() != null)
            {
                e.parent().removeChild(e);
            }

            e.parent(this);
            this._semicolon_.add(e);
        }
    }

    @Override
    public String toString()
    {
        return ""
            + toString(this._metaRoutine_)
            + toString(this._semicolon_);
    }

    @Override
    void removeChild(@SuppressWarnings("unused") Node child)
    {
        // Remove child
        if(this._metaRoutine_ == child)
        {
            this._metaRoutine_ = null;
            return;
        }

        if(this._semicolon_.remove(child))
        {
            return;
        }

        throw new RuntimeException("Not a child.");
    }

    @Override
    void replaceChild(@SuppressWarnings("unused") Node oldChild, @SuppressWarnings("unused") Node newChild)
    {
        // Replace child
        if(this._metaRoutine_ == oldChild)
        {
            setMetaRoutine((PMetaRoutine) newChild);
            return;
        }

        for(ListIterator<TSemicolon> i = this._semicolon_.listIterator(); i.hasNext();)
        {
            if(i.next() == oldChild)
            {
                if(newChild != null)
                {
                    i.set((TSemicolon) newChild);
                    newChild.parent(this);
                    oldChild.parent(null);
                    return;
                }

                i.remove();
                oldChild.parent(null);
                return;
            }
        }

        throw new RuntimeException("Not a child.");
    }
}
