<?xml version="1.0" encoding="UTF-8"?>
<StyledLayerDescriptor version="1.0.0"
    xsi:schemaLocation="http://www.opengis.net/sld StyledLayerDescriptor.xsd"
    xmlns="http://www.opengis.net/sld"
    xmlns:ogc="http://www.opengis.net/ogc"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <NamedLayer>
    <Name>biz_disaster_event</Name>
    <UserStyle>
      <Title>灾害影响范围</Title>
      <FeatureTypeStyle>
        <Rule>
          <Name>polygon-by-level</Name>
          <PolygonSymbolizer>
            <Fill>
              <CssParameter name="fill">
                <ogc:Function name="if_then_else">
                  <ogc:Function name="equalTo"><ogc:PropertyName>level</ogc:PropertyName><ogc:Literal>4</ogc:Literal></ogc:Function>
                  <ogc:Literal>#EF4444</ogc:Literal>
                  <ogc:Function name="if_then_else">
                    <ogc:Function name="equalTo"><ogc:PropertyName>level</ogc:PropertyName><ogc:Literal>3</ogc:Literal></ogc:Function>
                    <ogc:Literal>#F97316</ogc:Literal>
                    <ogc:Function name="if_then_else">
                      <ogc:Function name="equalTo"><ogc:PropertyName>level</ogc:PropertyName><ogc:Literal>2</ogc:Literal></ogc:Function>
                      <ogc:Literal>#FBBF24</ogc:Literal>
                      <ogc:Literal>#3B82F6</ogc:Literal>
                    </ogc:Function>
                  </ogc:Function>
                </ogc:Function>
              </CssParameter>
              <CssParameter name="fill-opacity">0.25</CssParameter>
            </Fill>
            <Stroke>
              <CssParameter name="stroke">#1F2937</CssParameter>
              <CssParameter name="stroke-width">1</CssParameter>
            </Stroke>
          </PolygonSymbolizer>
        </Rule>
      </FeatureTypeStyle>
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>
