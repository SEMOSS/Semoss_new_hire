/* This file was generated by SableCC (http://www.sablecc.org/). */

package prerna.sablecc2.node;

import prerna.sablecc2.analysis.*;

@SuppressWarnings("nls")
public final class ARepeatingOrComparison extends PRepeatingOrComparison
{
    private TOrComparator _orComparator_;
    private PBaseSimpleComparison _baseSimpleComparison_;

    public ARepeatingOrComparison()
    {
        // Constructor
    }

    public ARepeatingOrComparison(
        @SuppressWarnings("hiding") TOrComparator _orComparator_,
        @SuppressWarnings("hiding") PBaseSimpleComparison _baseSimpleComparison_)
    {
        // Constructor
        setOrComparator(_orComparator_);

        setBaseSimpleComparison(_baseSimpleComparison_);

    }

    @Override
    public Object clone()
    {
        return new ARepeatingOrComparison(
            cloneNode(this._orComparator_),
            cloneNode(this._baseSimpleComparison_));
    }

    @Override
    public void apply(Switch sw)
    {
        ((Analysis) sw).caseARepeatingOrComparison(this);
    }

    public TOrComparator getOrComparator()
    {
        return this._orComparator_;
    }

    public void setOrComparator(TOrComparator node)
    {
        if(this._orComparator_ != null)
        {
            this._orComparator_.parent(null);
        }

        if(node != null)
        {
            if(node.parent() != null)
            {
                node.parent().removeChild(node);
            }

            node.parent(this);
        }

        this._orComparator_ = node;
    }

    public PBaseSimpleComparison getBaseSimpleComparison()
    {
        return this._baseSimpleComparison_;
    }

    public void setBaseSimpleComparison(PBaseSimpleComparison node)
    {
        if(this._baseSimpleComparison_ != null)
        {
            this._baseSimpleComparison_.parent(null);
        }

        if(node != null)
        {
            if(node.parent() != null)
            {
                node.parent().removeChild(node);
            }

            node.parent(this);
        }

        this._baseSimpleComparison_ = node;
    }

    @Override
    public String toString()
    {
        return ""
            + toString(this._orComparator_)
            + toString(this._baseSimpleComparison_);
    }

    @Override
    void removeChild(@SuppressWarnings("unused") Node child)
    {
        // Remove child
        if(this._orComparator_ == child)
        {
            this._orComparator_ = null;
            return;
        }

        if(this._baseSimpleComparison_ == child)
        {
            this._baseSimpleComparison_ = null;
            return;
        }

        throw new RuntimeException("Not a child.");
    }

    @Override
    void replaceChild(@SuppressWarnings("unused") Node oldChild, @SuppressWarnings("unused") Node newChild)
    {
        // Replace child
        if(this._orComparator_ == oldChild)
        {
            setOrComparator((TOrComparator) newChild);
            return;
        }

        if(this._baseSimpleComparison_ == oldChild)
        {
            setBaseSimpleComparison((PBaseSimpleComparison) newChild);
            return;
        }

        throw new RuntimeException("Not a child.");
    }
}
