/* This file was generated by SableCC (http://www.sablecc.org/). */

package prerna.sablecc2.node;

import prerna.sablecc2.analysis.*;

@SuppressWarnings("nls")
public final class AProp extends PProp
{
    private TId _id_;
    private TEqual _equal_;
    private PScalar _scalar_;

    public AProp()
    {
        // Constructor
    }

    public AProp(
        @SuppressWarnings("hiding") TId _id_,
        @SuppressWarnings("hiding") TEqual _equal_,
        @SuppressWarnings("hiding") PScalar _scalar_)
    {
        // Constructor
        setId(_id_);

        setEqual(_equal_);

        setScalar(_scalar_);

    }

    @Override
    public Object clone()
    {
        return new AProp(
            cloneNode(this._id_),
            cloneNode(this._equal_),
            cloneNode(this._scalar_));
    }

    @Override
    public void apply(Switch sw)
    {
        ((Analysis) sw).caseAProp(this);
    }

    public TId getId()
    {
        return this._id_;
    }

    public void setId(TId node)
    {
        if(this._id_ != null)
        {
            this._id_.parent(null);
        }

        if(node != null)
        {
            if(node.parent() != null)
            {
                node.parent().removeChild(node);
            }

            node.parent(this);
        }

        this._id_ = node;
    }

    public TEqual getEqual()
    {
        return this._equal_;
    }

    public void setEqual(TEqual node)
    {
        if(this._equal_ != null)
        {
            this._equal_.parent(null);
        }

        if(node != null)
        {
            if(node.parent() != null)
            {
                node.parent().removeChild(node);
            }

            node.parent(this);
        }

        this._equal_ = node;
    }

    public PScalar getScalar()
    {
        return this._scalar_;
    }

    public void setScalar(PScalar node)
    {
        if(this._scalar_ != null)
        {
            this._scalar_.parent(null);
        }

        if(node != null)
        {
            if(node.parent() != null)
            {
                node.parent().removeChild(node);
            }

            node.parent(this);
        }

        this._scalar_ = node;
    }

    @Override
    public String toString()
    {
        return ""
            + toString(this._id_)
            + toString(this._equal_)
            + toString(this._scalar_);
    }

    @Override
    void removeChild(@SuppressWarnings("unused") Node child)
    {
        // Remove child
        if(this._id_ == child)
        {
            this._id_ = null;
            return;
        }

        if(this._equal_ == child)
        {
            this._equal_ = null;
            return;
        }

        if(this._scalar_ == child)
        {
            this._scalar_ = null;
            return;
        }

        throw new RuntimeException("Not a child.");
    }

    @Override
    void replaceChild(@SuppressWarnings("unused") Node oldChild, @SuppressWarnings("unused") Node newChild)
    {
        // Replace child
        if(this._id_ == oldChild)
        {
            setId((TId) newChild);
            return;
        }

        if(this._equal_ == oldChild)
        {
            setEqual((TEqual) newChild);
            return;
        }

        if(this._scalar_ == oldChild)
        {
            setScalar((PScalar) newChild);
            return;
        }

        throw new RuntimeException("Not a child.");
    }
}
