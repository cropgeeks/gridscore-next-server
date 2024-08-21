/* tslint:disable */
/* eslint-disable */
// Generated using typescript-generator version 3.2.1263 on 2024-08-21 16:57:16.

export class Trial {
    name: string;
    description: string;
    traits: Trait[];
    comments: Comment[];
    events: Event[];
    people: Person[];
    layout: Layout;
    data: { [index: string]: Cell };
    brapiId: string;
    brapiConfig: BrapiConfig;
    socialShareConfig: SocialShareConfig;
    updatedOn: string;
    createdOn: string;
    lastSyncedOn: string;
    shareCodes: ShareCodes;

    constructor(data: Trial) {
        this.name = data.name;
        this.description = data.description;
        this.traits = data.traits;
        this.comments = data.comments;
        this.events = data.events;
        this.people = data.people;
        this.layout = data.layout;
        this.data = data.data;
        this.brapiId = data.brapiId;
        this.brapiConfig = data.brapiConfig;
        this.socialShareConfig = data.socialShareConfig;
        this.updatedOn = data.updatedOn;
        this.createdOn = data.createdOn;
        this.lastSyncedOn = data.lastSyncedOn;
        this.shareCodes = data.shareCodes;
    }
}

export class Trait {
    id: string;
    brapiId: string;
    name: string;
    description: string;
    dataType: string;
    allowRepeats: boolean;
    setSize: number;
    group: Group;
    restrictions: Restrictions;
    timeframe: Timeframe;

    constructor(data: Trait) {
        this.id = data.id;
        this.brapiId = data.brapiId;
        this.name = data.name;
        this.description = data.description;
        this.dataType = data.dataType;
        this.allowRepeats = data.allowRepeats;
        this.setSize = data.setSize;
        this.group = data.group;
        this.restrictions = data.restrictions;
        this.timeframe = data.timeframe;
    }
}

export class Comment {
    timestamp: string;
    content: string;

    constructor(data: Comment) {
        this.timestamp = data.timestamp;
        this.content = data.content;
    }
}

export class Event {
    timestamp: string;
    content: string;
    type: EventType;
    impact: number;

    constructor(data: Event) {
        this.timestamp = data.timestamp;
        this.content = data.content;
        this.type = data.type;
        this.impact = data.impact;
    }
}

export class Person {
    id: string;
    name: string;
    email: string;
    types: PersonType[];

    constructor(data: Person) {
        this.id = data.id;
        this.name = data.name;
        this.email = data.email;
        this.types = data.types;
    }
}

export class Layout {
    rows: number;
    columns: number;
    corners: Corners;
    markers: Markers;
    columnOrder: string;
    rowOrder: string;
    columnLabels: number[];
    rowLabels: number[];

    constructor(data: Layout) {
        this.rows = data.rows;
        this.columns = data.columns;
        this.corners = data.corners;
        this.markers = data.markers;
        this.columnOrder = data.columnOrder;
        this.rowOrder = data.rowOrder;
        this.columnLabels = data.columnLabels;
        this.rowLabels = data.rowLabels;
    }
}

export class Cell {
    brapiId: string;
    germplasm: string;
    rep: string;
    isMarked: boolean;
    geography: Geography;
    measurements: { [index: string]: Measurement[] };
    comments: Comment[];
    categories: string[];

    constructor(data: Cell) {
        this.brapiId = data.brapiId;
        this.germplasm = data.germplasm;
        this.rep = data.rep;
        this.isMarked = data.isMarked;
        this.geography = data.geography;
        this.measurements = data.measurements;
        this.comments = data.comments;
        this.categories = data.categories;
    }
}

export class BrapiConfig {
    url: string;

    constructor(data: BrapiConfig) {
        this.url = data.url;
    }
}

export class SocialShareConfig {
    title: string;
    text: string;
    url: string;

    constructor(data: SocialShareConfig) {
        this.title = data.title;
        this.text = data.text;
        this.url = data.url;
    }
}

export class ShareCodes {
    ownerCode: string;
    editorCode: string;
    viewerCode: string;

    constructor(data: ShareCodes) {
        this.ownerCode = data.ownerCode;
        this.editorCode = data.editorCode;
        this.viewerCode = data.viewerCode;
    }
}

export class Group {
    name: string;

    constructor(data: Group) {
        this.name = data.name;
    }
}

export class Restrictions {
    min: number;
    max: number;
    categories: string[];

    constructor(data: Restrictions) {
        this.min = data.min;
        this.max = data.max;
        this.categories = data.categories;
    }
}

export class Timeframe {
    start: string;
    end: string;
    type: TimeframeType;

    constructor(data: Timeframe) {
        this.start = data.start;
        this.end = data.end;
        this.type = data.type;
    }
}

export class Corners {
    topLeft: LatLng;
    topRight: LatLng;
    bottomRight: LatLng;
    bottomLeft: LatLng;
    valid: boolean;

    constructor(data: Corners) {
        this.topLeft = data.topLeft;
        this.topRight = data.topRight;
        this.bottomRight = data.bottomRight;
        this.bottomLeft = data.bottomLeft;
        this.valid = data.valid;
    }
}

export class Markers {
    anchor: Anchor;
    everyRow: number;
    everyColumn: number;

    constructor(data: Markers) {
        this.anchor = data.anchor;
        this.everyRow = data.everyRow;
        this.everyColumn = data.everyColumn;
    }
}

export class Geography {
    corners: Corners;
    center: LatLng;

    constructor(data: Geography) {
        this.corners = data.corners;
        this.center = data.center;
    }
}

export class Measurement {
    personId: string;
    timestamp: string;
    values: string[];

    constructor(data: Measurement) {
        this.personId = data.personId;
        this.timestamp = data.timestamp;
        this.values = data.values;
    }
}

export class LatLng {
    lat: number;
    lng: number;
    valid: boolean;

    constructor(data: LatLng) {
        this.lat = data.lat;
        this.lng = data.lng;
        this.valid = data.valid;
    }
}

export const enum EventType {
    WEATHER = 'WEATHER',
    MANAGEMENT = 'MANAGEMENT',
    OTHER = 'OTHER',
}

export const enum PersonType {
    DATA_COLLECTOR = 'DATA_COLLECTOR',
    DATA_SUBMITTER = 'DATA_SUBMITTER',
    AUTHOR = 'AUTHOR',
    CORRESPONDING_AUTHOR = 'CORRESPONDING_AUTHOR',
    QUALITY_CHECKER = 'QUALITY_CHECKER',
}

export const enum TimeframeType {
    SUGGEST = 'SUGGEST',
    ENFORCE = 'ENFORCE',
}

export const enum Anchor {
    topLeft = 'topLeft',
    topRight = 'topRight',
    bottomRight = 'bottomRight',
    bottomLeft = 'bottomLeft',
}
