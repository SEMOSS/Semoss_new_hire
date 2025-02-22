/* This file was generated by SableCC (http://www.sablecc.org/). */

package prerna.sablecc2.node;

import prerna.sablecc2.analysis.*;

@SuppressWarnings("nls")
public final class AImplicitRelationship extends PRelationship
{
    private PImplicitRel _implicitRel_;

    public AImplicitRelationship()
    {
        // Constructor
    }

    public AImplicitRelationship(
        @SuppressWarnings("hiding") PImplicitRel _implicitRel_)
    {
        // Constructor
        setImplicitRel(_implicitRel_);

    }

    @Override
    public Object clone()
    {
        return new AImplicitRelationship(
            cloneNode(this._implicitRel_));
    }

    @Override
    public void apply(Switch sw)
    {
        ((Analysis) sw).caseAImplicitRelationship(this);
    }

    public PImplicitRel getImplicitRel()
    {
        return this._implicitRel_;
    }

    public void setImplicitRel(PImplicitRel node)
    {
        if(this._implicitRel_ != null)
        {
            this._implicitRel_.parent(null);
        }

        if(node != null)
        {
            if(node.parent() != null)
            {
                node.parent().removeChild(node);
            }

            node.parent(this);
        }

        this._implicitRel_ = node;
    }

    @Override
    public String toString()
    {
        return ""
            + toString(this._implicitRel_);
    }

    @Override
    void removeChild(@SuppressWarnings("unused") Node child)
    {
        // Remove child
        if(this._implicitRel_ == child)
        {
            this._implicitRel_ = null;
            return;
        }

        throw new RuntimeException("Not a child.");
    }

    @Override
    void replaceChild(@SuppressWarnings("unused") Node oldChild, @SuppressWarnings("unused") Node newChild)
    {
        // Replace child
        if(this._implicitRel_ == oldChild)
        {
            setImplicitRel((PImplicitRel) newChild);
            return;
        }

        throw new RuntimeException("Not a child.");
    }
}
