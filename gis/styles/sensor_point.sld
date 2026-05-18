<?xml version="1.0" encoding="UTF-8"?>
<StyledLayerDescriptor version="1.0.0"
    xsi:schemaLocation="http://www.opengis.net/sld StyledLayerDescriptor.xsd"
    xmlns="http://www.opengis.net/sld"
    xmlns:ogc="http://www.opengis.net/ogc"
    xmlns:xlink="http://www.w3.org/1999/xlink"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <NamedLayer>
    <Name>biz_sensor</Name>
    <UserStyle>
      <Title>传感器点位样式</Title>
      <FeatureTypeStyle>
        <Rule>
          <Name>sensor</Name>
          <PointSymbolizer>
            <Graphic>
              <Mark>
                <WellKnownName>circle</WellKnownName>
                <Fill><CssParameter name="fill">#22C55E</CssParameter></Fill>
                <Stroke><CssParameter name="stroke">#FFFFFF</CssParameter><CssParameter name="stroke-width">1</CssParameter></Stroke>
              </Mark>
              <Size>10</Size>
            </Graphic>
          </PointSymbolizer>
          <TextSymbolizer>
            <Label><ogc:PropertyName>name</ogc:PropertyName></Label>
            <Font><CssParameter name="font-family">sans-serif</CssParameter><CssParameter name="font-size">10</CssParameter></Font>
            <LabelPlacement><PointPlacement><Displacement><DisplacementX>0</DisplacementX><DisplacementY>14</DisplacementY></Displacement></PointPlacement></LabelPlacement>
            <Fill><CssParameter name="fill">#1F2937</CssParameter></Fill>
            <Halo><Radius>1</Radius><Fill><CssParameter name="fill">#FFFFFF</CssParameter></Fill></Halo>
          </TextSymbolizer>
        </Rule>
      </FeatureTypeStyle>
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>
