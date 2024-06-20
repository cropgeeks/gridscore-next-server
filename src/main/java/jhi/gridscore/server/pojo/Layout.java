package jhi.gridscore.server.pojo;

import lombok.*;
import lombok.experimental.Accessors;

import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class Layout
{
	public static final String DISPLAY_ORDER_TOP_TO_BOTTOM = "TOP_TO_BOTTOM";
	public static final String DISPLAY_ORDER_BOTTOM_TO_TOP = "BOTTOM_TO_TOP";
	public static final String DISPLAY_ORDER_LEFT_TO_RIGHT = "LEFT_TO_RIGHT";
	public static final String DISPLAY_ORDER_RIGHT_TO_LEFT = "RIGHT_TO_LEFT";

	private int           rows;
	private int           columns;
	private Corners       corners;
	private Markers       markers;
	private String        columnOrder = DISPLAY_ORDER_LEFT_TO_RIGHT;
	private String        rowOrder    = DISPLAY_ORDER_TOP_TO_BOTTOM;
	private List<Integer> columnLabels;
	private List<Integer> rowLabels;
}
