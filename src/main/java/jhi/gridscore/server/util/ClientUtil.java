package jhi.gridscore.server.util;

import jhi.gridscore.server.pojo.*;
import lombok.*;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.CollectionUtils;
import org.jooq.tools.StringUtils;

import java.io.*;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ClientUtil
{
	@Getter
	@Setter
	@Accessors(chain = true)
	@NoArgsConstructor
	@AllArgsConstructor
	@ToString
	private static class Value
	{
		private int    index;
		private String value;
	}

	public static void exportTabLongFormat(Trial trial, File target)
	{
		List<String> result = new ArrayList<>();
		result.add("Germplasm\tRep\tRow\tColumn\tSet entry\tDate\tLatitude\tLongitude\tTrait group\tTrait\tValue\tPerson");

		trial.getData().forEach((key, v) -> {
			if (v.getMeasurements() != null)
			{
				List<Integer> rowColumn = Arrays.stream(key.split("\\|")).map(c -> Integer.parseInt(c)).toList();
				int row = getRowLabel(trial.getLayout(), rowColumn.get(0));
				int column = getColumnLabel(trial.getLayout(), rowColumn.get(1));
				String germplasmMeta = v.getGermplasm() + "\t" + (StringUtils.isBlank(v.getRep()) ? "" : v.getRep()) + "\t" + row + "\t" + column;

				final Cell vv = v;
				trial.getTraits().forEach(t -> {
					List<Measurement> td = vv.getMeasurements().get(t.getId());

					if (!CollectionUtils.isEmpty(td))
					{
						td.forEach(dp -> {
							List<String> values = dp.getValues();
							if (values == null)
								values = new ArrayList<>();

							List<Value> mapped = new ArrayList<>();
							for (int i = 0; i < values.size(); i++)
								mapped.add(new Value(i, values.get(i)));

							mapped = mapped.stream().filter(val -> val != null && val.value != null && !StringUtils.isBlank(val.value)).toList();

							if (!CollectionUtils.isEmpty(mapped))
							{
								for (Value val : mapped)
								{
									List<String> valuesss = new ArrayList<>();

									if (Objects.equals(t.getDataType(), "multicat"))
										valuesss = Arrays.stream(val.value.split(":")).toList();
									else
										valuesss.add(val.value);

									for (String value : valuesss)
									{
										ZonedDateTime dateTime = ZonedDateTime.parse(dp.getTimestamp(), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXX"));
										String formatted = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

										String catValue = ((Objects.equals(t.getDataType(), "categorical") || Objects.equals(t.getDataType(), "multicat")) && t.getRestrictions() != null && !CollectionUtils.isEmpty(t.getRestrictions().getCategories())) ? t.getRestrictions().getCategories().get(Integer.parseInt(value)) : value;
										String str = germplasmMeta + "\t" + (val.index + 1) + "\t" + formatted + getLatLngAverage(v.getGeography()) + "\t" + (t.getGroup() != null ? t.getGroup().getName() : "") + "\t" + t.getName() + "\t" + catValue + "\t";

										if (!CollectionUtils.isEmpty(trial.getPeople()))
										{
											Person pp = trial.getPeople().stream().filter(p -> Objects.equals(p.getId(), dp.getPersonId())).findFirst().orElseGet(() -> null);

											if (pp != null)
												str += pp.getName();
										}

										result.add(str);
									}
								}
							}
						});
					}
				});
			}
		});

		try
		{
			target.getParentFile().mkdirs();
			Files.write(target.toPath(), String.join("\n", result).getBytes());
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	private static String getLatLngAverage(Geography geography)
	{
		if (geography == null)
		{
			return "\t\t";
		}
		else
		{
			if (geography.getCenter() != null)
			{
				return "\t" + geography.getCenter().getLat() + "\t" + geography.getCenter().getLng();
			}
			else if (geography.getCorners() != null)
			{
				double latverage = 0;
				double lngverage = 0;
				int count = 0;

				if (geography.getCorners().getTopLeft() != null)
				{
					latverage += geography.getCorners().getTopLeft().getLat();
					lngverage += geography.getCorners().getTopLeft().getLng();
					count++;
				}
				if (geography.getCorners().getTopRight() != null)
				{
					latverage += geography.getCorners().getTopRight().getLat();
					lngverage += geography.getCorners().getTopRight().getLng();
					count++;
				}
				if (geography.getCorners().getBottomRight() != null)
				{
					latverage += geography.getCorners().getBottomRight().getLat();
					lngverage += geography.getCorners().getBottomRight().getLng();
					count++;
				}
				if (geography.getCorners().getBottomLeft() != null)
				{
					latverage += geography.getCorners().getBottomLeft().getLat();
					lngverage += geography.getCorners().getBottomLeft().getLng();
					count++;
				}

				if (count > 0)
					return "\t" + (latverage / count) + "\t" + (lngverage / count);
				else
					return "\t\t";
			}
			else
			{
				return "\t\t";
			}
		}
	}

	public static int getRowLabel(Layout layout, int index)
	{
		if (layout.getRowLabels() != null && layout.getRowLabels().size() == layout.getRows())
		{
			return layout.getRowLabels().get(index);
		}
		else
		{
			return Objects.equals(layout.getRowOrder(), "BOTTOM_TO_TOP") ? (layout.getRows() - index) : (index + 1);
		}
	}

	public static int getColumnLabel(Layout layout, int index)
	{
		if (layout.getColumnLabels() != null && layout.getColumnLabels().size() == layout.getColumns())
		{
			return layout.getColumnLabels().get(index);
		}
		else
		{
			return Objects.equals(layout.getColumnOrder(), "RIGHT_TO_LEFT") ? (layout.getColumns() - index) : (index + 1);
		}
	}
}
