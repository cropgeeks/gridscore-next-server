package jhi.gridscore.server.database.binding;

import com.google.gson.*;
import jhi.gridscore.server.pojo.Trial;
import org.jooq.*;
import org.jooq.conf.ParamType;
import org.jooq.impl.DSL;

import java.sql.*;
import java.util.Objects;

/**
 * @author Sebastian Raubach
 */
public class TrialBinding implements Binding<JSON, Trial>
{
	@Override
	public Converter<JSON, Trial> converter()
	{
		Gson gson = new Gson();
		return new Converter<>()
		{
			@Override
			public Trial from(JSON o)
			{
				return o == null ? null : gson.fromJson(Objects.toString(o), Trial.class);
			}

			@Override
			public JSON to(Trial o)
			{
				return o == null ? null : JSON.json(gson.toJson(o));
			}

			@Override
			public Class<JSON> fromType()
			{
				return JSON.class;
			}

			@Override
			public Class<Trial> toType()
			{
				return Trial.class;
			}
		};
	}

	@Override
	public void sql(BindingSQLContext<Trial> ctx)
		throws SQLException
	{
		// Depending on how you generate your SQL, you may need to explicitly distinguish
		// between jOOQ generating bind variables or inlined literals.
		if (ctx.render().paramType() == ParamType.INLINED)
			ctx.render().visit(DSL.inline(ctx.convert(converter()).value())).sql("");
		else
			ctx.render().sql("?");
	}

	@Override
	public void register(BindingRegisterContext<Trial> ctx)
		throws SQLException
	{
		ctx.statement().registerOutParameter(ctx.index(), Types.VARCHAR);
	}

	@Override
	public void set(BindingSetStatementContext<Trial> ctx)
		throws SQLException
	{
		ctx.statement().setString(ctx.index(), Objects.toString(ctx.convert(converter()).value(), null));
	}

	@Override
	public void set(BindingSetSQLOutputContext<Trial> ctx)
		throws SQLException
	{
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void get(BindingGetResultSetContext<Trial> ctx)
		throws SQLException
	{
		ctx.convert(converter()).value(JSON.json(ctx.resultSet().getString(ctx.index())));
	}

	@Override
	public void get(BindingGetStatementContext<Trial> ctx)
		throws SQLException
	{
		ctx.convert(converter()).value(JSON.json(ctx.statement().getString(ctx.index())));
	}

	@Override
	public void get(BindingGetSQLInputContext<Trial> ctx)
		throws SQLException
	{
		throw new SQLFeatureNotSupportedException();
	}
}
