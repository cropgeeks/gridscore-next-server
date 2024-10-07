package jhi.gridscore.server.database.binding;

import com.google.gson.Gson;
import jhi.gridscore.server.pojo.UpdateStats;
import org.jooq.*;
import org.jooq.conf.ParamType;
import org.jooq.impl.DSL;

import java.sql.*;
import java.util.Objects;

/**
 * @author Sebastian Raubach
 */
public class UpdateStatsBinding implements Binding<JSON, UpdateStats>
{
	@Override
	public Converter<JSON, UpdateStats> converter()
	{
		Gson gson = new Gson();
		return new Converter<>()
		{
			@Override
			public UpdateStats from(JSON o)
			{
				return o == null ? null : gson.fromJson(Objects.toString(o), UpdateStats.class);
			}

			@Override
			public JSON to(UpdateStats o)
			{
				return o == null ? null : JSON.json(gson.toJson(o));
			}

			@Override
			public Class<JSON> fromType()
			{
				return JSON.class;
			}

			@Override
			public Class<UpdateStats> toType()
			{
				return UpdateStats.class;
			}
		};
	}

	@Override
	public void sql(BindingSQLContext<UpdateStats> ctx)
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
	public void register(BindingRegisterContext<UpdateStats> ctx)
		throws SQLException
	{
		ctx.statement().registerOutParameter(ctx.index(), Types.VARCHAR);
	}

	@Override
	public void set(BindingSetStatementContext<UpdateStats> ctx)
		throws SQLException
	{
		ctx.statement().setString(ctx.index(), Objects.toString(ctx.convert(converter()).value(), null));
	}

	@Override
	public void set(BindingSetSQLOutputContext<UpdateStats> ctx)
		throws SQLException
	{
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void get(BindingGetResultSetContext<UpdateStats> ctx)
		throws SQLException
	{
		ctx.convert(converter()).value(JSON.json(ctx.resultSet().getString(ctx.index())));
	}

	@Override
	public void get(BindingGetStatementContext<UpdateStats> ctx)
		throws SQLException
	{
		ctx.convert(converter()).value(JSON.json(ctx.statement().getString(ctx.index())));
	}

	@Override
	public void get(BindingGetSQLInputContext<UpdateStats> ctx)
		throws SQLException
	{
		throw new SQLFeatureNotSupportedException();
	}
}
