/* This file was generated by SableCC (http://www.sablecc.org/). */

package prerna.sablecc2.node;

import prerna.sablecc2.analysis.*;

@SuppressWarnings("nls")
public final class AEmptyList extends PList
{
    private PNoValuesList _noValuesList_;

    public AEmptyList()
    {
        // Constructor
    }

    public AEmptyList(
        @SuppressWarnings("hiding") PNoValuesList _noValuesList_)
    {
        // Constructor
        setNoValuesList(_noValuesList_);

    }

    @Override
    public Object clone()
    {
        return new AEmptyList(
            cloneNode(this._noValuesList_));
    }

    @Override
    public void apply(Switch sw)
    {
        ((Analysis) sw).caseAEmptyList(this);
    }

    public PNoValuesList getNoValuesList()
    {
        return this._noValuesList_;
    }

    public void setNoValuesList(PNoValuesList node)
    {
        if(this._noValuesList_ != null)
        {
            this._noValuesList_.parent(null);
        }

        if(node != null)
        {
            if(node.parent() != null)
            {
                node.parent().removeChild(node);
            }

            node.parent(this);
        }

        this._noValuesList_ = node;
    }

    @Override
    public String toString()
    {
        return ""
            + toString(this._noValuesList_);
    }

    @Override
    void removeChild(@SuppressWarnings("unused") Node child)
    {
        // Remove child
        if(this._noValuesList_ == child)
        {
            this._noValuesList_ = null;
            return;
        }

        throw new RuntimeException("Not a child.");
    }

    @Override
    void replaceChild(@SuppressWarnings("unused") Node oldChild, @SuppressWarnings("unused") Node newChild)
    {
        // Replace child
        if(this._noValuesList_ == oldChild)
        {
            setNoValuesList((PNoValuesList) newChild);
            return;
        }

        throw new RuntimeException("Not a child.");
    }
}
