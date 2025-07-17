我来分析 mcp-client、mcp-router、mcp-server-v2 的系统调用链路。让我先探索这三个组件的架构和相互关系。

现在让我继续了解更多关于服务间通信的细节，特别是JSON-RPC协议和SSE通信机制。

现在让我查看一些关键的配置文件，了解端口和连接信息：


现在我来创建一个详细的系统调用链路分析报告：
<svg aria-roledescription="flowchart-v2" role="graphics-document document" viewBox="-7.5 -8 1643.453125 337" style="max-width: 1643.453125px;" xmlns="http://www.w3.org/2000/svg" width="100%" id="mermaid-svg-1752396460251-n8aw4g8p8"><style>#mermaid-svg-1752396460251-n8aw4g8p8{font-family:"trebuchet ms",verdana,arial,sans-serif;font-size:16px;fill:rgba(204, 204, 204, 0.87);}#mermaid-svg-1752396460251-n8aw4g8p8 .error-icon{fill:#bf616a;}#mermaid-svg-1752396460251-n8aw4g8p8 .error-text{fill:#bf616a;stroke:#bf616a;}#mermaid-svg-1752396460251-n8aw4g8p8 .edge-thickness-normal{stroke-width:2px;}#mermaid-svg-1752396460251-n8aw4g8p8 .edge-thickness-thick{stroke-width:3.5px;}#mermaid-svg-1752396460251-n8aw4g8p8 .edge-pattern-solid{stroke-dasharray:0;}#mermaid-svg-1752396460251-n8aw4g8p8 .edge-pattern-dashed{stroke-dasharray:3;}#mermaid-svg-1752396460251-n8aw4g8p8 .edge-pattern-dotted{stroke-dasharray:2;}#mermaid-svg-1752396460251-n8aw4g8p8 .marker{fill:rgba(204, 204, 204, 0.87);stroke:rgba(204, 204, 204, 0.87);}#mermaid-svg-1752396460251-n8aw4g8p8 .marker.cross{stroke:rgba(204, 204, 204, 0.87);}#mermaid-svg-1752396460251-n8aw4g8p8 svg{font-family:"trebuchet ms",verdana,arial,sans-serif;font-size:16px;}#mermaid-svg-1752396460251-n8aw4g8p8 .label{font-family:"trebuchet ms",verdana,arial,sans-serif;color:rgba(204, 204, 204, 0.87);}#mermaid-svg-1752396460251-n8aw4g8p8 .cluster-label text{fill:#ffffff;}#mermaid-svg-1752396460251-n8aw4g8p8 .cluster-label span,#mermaid-svg-1752396460251-n8aw4g8p8 p{color:#ffffff;}#mermaid-svg-1752396460251-n8aw4g8p8 .label text,#mermaid-svg-1752396460251-n8aw4g8p8 span,#mermaid-svg-1752396460251-n8aw4g8p8 p{fill:rgba(204, 204, 204, 0.87);color:rgba(204, 204, 204, 0.87);}#mermaid-svg-1752396460251-n8aw4g8p8 .node rect,#mermaid-svg-1752396460251-n8aw4g8p8 .node circle,#mermaid-svg-1752396460251-n8aw4g8p8 .node ellipse,#mermaid-svg-1752396460251-n8aw4g8p8 .node polygon,#mermaid-svg-1752396460251-n8aw4g8p8 .node path{fill:#1a1a1a;stroke:#2a2a2a;stroke-width:1px;}#mermaid-svg-1752396460251-n8aw4g8p8 .flowchart-label text{text-anchor:middle;}#mermaid-svg-1752396460251-n8aw4g8p8 .node .label{text-align:center;}#mermaid-svg-1752396460251-n8aw4g8p8 .node.clickable{cursor:pointer;}#mermaid-svg-1752396460251-n8aw4g8p8 .arrowheadPath{fill:#e5e5e5;}#mermaid-svg-1752396460251-n8aw4g8p8 .edgePath .path{stroke:rgba(204, 204, 204, 0.87);stroke-width:2.0px;}#mermaid-svg-1752396460251-n8aw4g8p8 .flowchart-link{stroke:rgba(204, 204, 204, 0.87);fill:none;}#mermaid-svg-1752396460251-n8aw4g8p8 .edgeLabel{background-color:#1a1a1a99;text-align:center;}#mermaid-svg-1752396460251-n8aw4g8p8 .edgeLabel rect{opacity:0.5;background-color:#1a1a1a99;fill:#1a1a1a99;}#mermaid-svg-1752396460251-n8aw4g8p8 .labelBkg{background-color:rgba(26, 26, 26, 0.5);}#mermaid-svg-1752396460251-n8aw4g8p8 .cluster rect{fill:rgba(64, 64, 64, 0.47);stroke:#30373a;stroke-width:1px;}#mermaid-svg-1752396460251-n8aw4g8p8 .cluster text{fill:#ffffff;}#mermaid-svg-1752396460251-n8aw4g8p8 .cluster span,#mermaid-svg-1752396460251-n8aw4g8p8 p{color:#ffffff;}#mermaid-svg-1752396460251-n8aw4g8p8 div.mermaidTooltip{position:absolute;text-align:center;max-width:200px;padding:2px;font-family:"trebuchet ms",verdana,arial,sans-serif;font-size:12px;background:#88c0d0;border:1px solid #30373a;border-radius:2px;pointer-events:none;z-index:100;}#mermaid-svg-1752396460251-n8aw4g8p8 .flowchartTitleText{text-anchor:middle;font-size:18px;fill:rgba(204, 204, 204, 0.87);}#mermaid-svg-1752396460251-n8aw4g8p8 :root{--mermaid-font-family:"trebuchet ms",verdana,arial,sans-serif;}</style><g><marker orient="auto" markerHeight="12" markerWidth="12" markerUnits="userSpaceOnUse" refY="5" refX="6" viewBox="0 0 10 10" class="marker flowchart" id="mermaid-svg-1752396460251-n8aw4g8p8_flowchart-pointEnd"><path style="stroke-width: 1; stroke-dasharray: 1, 0;" class="arrowMarkerPath" d="M 0 0 L 10 5 L 0 10 z"/></marker><marker orient="auto" markerHeight="12" markerWidth="12" markerUnits="userSpaceOnUse" refY="5" refX="4.5" viewBox="0 0 10 10" class="marker flowchart" id="mermaid-svg-1752396460251-n8aw4g8p8_flowchart-pointStart"><path style="stroke-width: 1; stroke-dasharray: 1, 0;" class="arrowMarkerPath" d="M 0 5 L 10 10 L 10 0 z"/></marker><marker orient="auto" markerHeight="11" markerWidth="11" markerUnits="userSpaceOnUse" refY="5" refX="11" viewBox="0 0 10 10" class="marker flowchart" id="mermaid-svg-1752396460251-n8aw4g8p8_flowchart-circleEnd"><circle style="stroke-width: 1; stroke-dasharray: 1, 0;" class="arrowMarkerPath" r="5" cy="5" cx="5"/></marker><marker orient="auto" markerHeight="11" markerWidth="11" markerUnits="userSpaceOnUse" refY="5" refX="-1" viewBox="0 0 10 10" class="marker flowchart" id="mermaid-svg-1752396460251-n8aw4g8p8_flowchart-circleStart"><circle style="stroke-width: 1; stroke-dasharray: 1, 0;" class="arrowMarkerPath" r="5" cy="5" cx="5"/></marker><marker orient="auto" markerHeight="11" markerWidth="11" markerUnits="userSpaceOnUse" refY="5.2" refX="12" viewBox="0 0 11 11" class="marker cross flowchart" id="mermaid-svg-1752396460251-n8aw4g8p8_flowchart-crossEnd"><path style="stroke-width: 2; stroke-dasharray: 1, 0;" class="arrowMarkerPath" d="M 1,1 l 9,9 M 10,1 l -9,9"/></marker><marker orient="auto" markerHeight="11" markerWidth="11" markerUnits="userSpaceOnUse" refY="5.2" refX="-1" viewBox="0 0 11 11" class="marker cross flowchart" id="mermaid-svg-1752396460251-n8aw4g8p8_flowchart-crossStart"><path style="stroke-width: 2; stroke-dasharray: 1, 0;" class="arrowMarkerPath" d="M 1,1 l 9,9 M 10,1 l -9,9"/></marker><g class="root"><g class="clusters"/><g class="edgePaths"><path marker-end="url(#mermaid-svg-1752396460251-n8aw4g8p8_flowchart-pointEnd)" style="fill:none;" class="edge-thickness-normal edge-pattern-solid flowchart-link LS-A LE-B" id="L-A-B-0" d="M1581.945,81L1581.945,89.333C1581.945,97.667,1581.945,114.333,1580.17,126.29C1578.395,138.247,1574.845,145.494,1573.07,149.117L1571.295,152.74"/><path marker-end="url(#mermaid-svg-1752396460251-n8aw4g8p8_flowchart-pointEnd)" style="fill:none;" class="edge-thickness-normal edge-pattern-solid flowchart-link LS-B LE-C" id="L-B-C-0" d="M1568.963,213.5L1571.127,217.917C1573.291,222.333,1577.618,231.167,1573.608,239.295C1569.598,247.423,1557.251,254.846,1551.078,258.558L1544.904,262.269"/><path marker-end="url(#mermaid-svg-1752396460251-n8aw4g8p8_flowchart-pointEnd)" style="fill:none;" class="edge-thickness-normal edge-pattern-solid flowchart-link LS-B LE-D" id="L-B-D-0" d="M1520.23,213.5L1514.707,217.917C1509.183,222.333,1498.137,231.167,1476.461,240.914C1454.784,250.661,1422.479,261.323,1406.326,266.653L1390.174,271.984"/><path marker-end="url(#mermaid-svg-1752396460251-n8aw4g8p8_flowchart-pointEnd)" style="fill:none;" class="edge-thickness-normal edge-pattern-solid flowchart-link LS-B LE-E" id="L-B-E-0" d="M1509.955,213.5L1502.811,217.917C1495.666,222.333,1481.378,231.167,1433.564,242.584C1385.749,254.002,1304.408,268.004,1263.737,275.004L1223.067,282.005"/><path marker-end="url(#mermaid-svg-1752396460251-n8aw4g8p8_flowchart-pointEnd)" style="fill:none;" class="edge-thickness-normal edge-pattern-solid flowchart-link LS-F LE-B" id="L-F-B-0" d="M1445.094,82.5L1450.427,90.583C1455.759,98.667,1466.425,114.833,1477.366,126.828C1488.308,138.823,1499.527,146.646,1505.136,150.557L1510.745,154.468"/><path marker-end="url(#mermaid-svg-1752396460251-n8aw4g8p8_flowchart-pointEnd)" style="fill:none;" class="edge-thickness-normal edge-pattern-solid flowchart-link LS-F LE-C" id="L-F-C-0" d="M1425.633,82.5L1425.633,90.583C1425.633,98.667,1425.633,114.833,1425.633,132C1425.633,149.167,1425.633,167.333,1425.633,185.5C1425.633,203.667,1425.633,221.833,1430.294,234.541C1434.955,247.249,1444.276,254.498,1448.937,258.122L1453.598,261.747"/><path marker-end="url(#mermaid-svg-1752396460251-n8aw4g8p8_flowchart-pointEnd)" style="fill:none;" class="edge-thickness-normal edge-pattern-solid flowchart-link LS-F LE-D" id="L-F-D-0" d="M1390.214,82.5L1380.509,90.583C1370.804,98.667,1351.394,114.833,1341.689,132C1331.984,149.167,1331.984,167.333,1331.984,185.5C1331.984,203.667,1331.984,221.833,1331.644,234.205C1331.303,246.576,1330.621,253.152,1330.281,256.44L1329.94,259.728"/><path marker-end="url(#mermaid-svg-1752396460251-n8aw4g8p8_flowchart-pointEnd)" style="fill:none;" class="edge-thickness-normal edge-pattern-solid flowchart-link LS-F LE-E" id="L-F-E-0" d="M1365.328,65.901L1314.613,76.751C1263.898,87.601,1162.469,109.3,1111.754,129.234C1061.039,149.167,1061.039,167.333,1061.039,185.5C1061.039,203.667,1061.039,221.833,1067.978,234.664C1074.918,247.494,1088.797,254.988,1095.736,258.735L1102.676,262.482"/><path marker-end="url(#mermaid-svg-1752396460251-n8aw4g8p8_flowchart-pointEnd)" style="fill:none;" class="edge-thickness-normal edge-pattern-solid flowchart-link LS-G LE-C" id="L-G-C-0" d="M1234.68,215L1241.075,219.167C1247.47,223.333,1260.261,231.667,1292.812,242.113C1325.363,252.56,1377.675,265.121,1403.831,271.401L1429.987,277.681"/><path marker-end="url(#mermaid-svg-1752396460251-n8aw4g8p8_flowchart-pointEnd)" style="fill:none;" class="edge-thickness-normal edge-pattern-solid flowchart-link LS-G LE-D" id="L-G-D-0" d="M1183.3,215L1182.438,219.167C1181.576,223.333,1179.853,231.667,1193.111,240.878C1206.37,250.089,1234.611,260.177,1248.732,265.222L1262.853,270.266"/><path marker-end="url(#mermaid-svg-1752396460251-n8aw4g8p8_flowchart-pointEnd)" style="fill:none;" class="edge-thickness-normal edge-pattern-solid flowchart-link LS-G LE-E" id="L-G-E-0" d="M1165.199,215L1161.78,219.167C1158.362,223.333,1151.525,231.667,1149.013,239.148C1146.502,246.629,1148.317,253.259,1149.224,256.573L1150.132,259.888"/></g><g class="edgeLabels"><g class="edgeLabel"><g transform="translate(0, 0)" class="label"><foreignObject height="0" width="0"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="edgeLabel"></span></div></foreignObject></g></g><g class="edgeLabel"><g transform="translate(0, 0)" class="label"><foreignObject height="0" width="0"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="edgeLabel"></span></div></foreignObject></g></g><g class="edgeLabel"><g transform="translate(0, 0)" class="label"><foreignObject height="0" width="0"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="edgeLabel"></span></div></foreignObject></g></g><g class="edgeLabel"><g transform="translate(0, 0)" class="label"><foreignObject height="0" width="0"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="edgeLabel"></span></div></foreignObject></g></g><g class="edgeLabel"><g transform="translate(0, 0)" class="label"><foreignObject height="0" width="0"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="edgeLabel"></span></div></foreignObject></g></g><g class="edgeLabel"><g transform="translate(0, 0)" class="label"><foreignObject height="0" width="0"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="edgeLabel"></span></div></foreignObject></g></g><g class="edgeLabel"><g transform="translate(0, 0)" class="label"><foreignObject height="0" width="0"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="edgeLabel"></span></div></foreignObject></g></g><g class="edgeLabel"><g transform="translate(0, 0)" class="label"><foreignObject height="0" width="0"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="edgeLabel"></span></div></foreignObject></g></g><g class="edgeLabel"><g transform="translate(0, 0)" class="label"><foreignObject height="0" width="0"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="edgeLabel"></span></div></foreignObject></g></g><g class="edgeLabel"><g transform="translate(0, 0)" class="label"><foreignObject height="0" width="0"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="edgeLabel"></span></div></foreignObject></g></g><g class="edgeLabel"><g transform="translate(0, 0)" class="label"><foreignObject height="0" width="0"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="edgeLabel"></span></div></foreignObject></g></g></g><g class="nodes"><g transform="translate(-7.5, 1.5)" class="root"><g class="clusters"><g id="服务发现" class="cluster default flowchart-label"><rect height="87" width="490.21875" y="8" x="8" ry="0" rx="0" style=""/><g transform="translate(221.109375, 8)" class="cluster-label"><foreignObject height="22" width="64"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="nodeLabel">服务发现</span></div></foreignObject></g></g></g><g class="edgePaths"/><g class="edgeLabels"/><g class="nodes"><g transform="translate(103.3046875, 51.5)" id="flowchart-K-1893" class="node default default flowchart-label"><rect height="37" width="120.609375" y="-18.5" x="-60.3046875" ry="0" rx="0" style="" class="basic label-container"/><g transform="translate(-52.8046875, -11)" style="" class="label"><rect/><foreignObject height="22" width="105.609375"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="nodeLabel">Nacos服务注册</span></div></foreignObject></g></g><g transform="translate(273.9140625, 51.5)" id="flowchart-L-1894" class="node default default flowchart-label"><rect height="37" width="120.609375" y="-18.5" x="-60.3046875" ry="0" rx="0" style="" class="basic label-container"/><g transform="translate(-52.8046875, -11)" style="" class="label"><rect/><foreignObject height="22" width="105.609375"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="nodeLabel">Nacos服务发现</span></div></foreignObject></g></g><g transform="translate(423.71875, 51.5)" id="flowchart-M-1895" class="node default default flowchart-label"><rect height="37" width="79" y="-18.5" x="-39.5" ry="0" rx="0" style="" class="basic label-container"/><g transform="translate(-32, -11)" style="" class="label"><rect/><foreignObject height="22" width="64"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="nodeLabel">健康检查</span></div></foreignObject></g></g></g></g><g transform="translate(532.71875, -8)" class="root"><g class="clusters"><g id="通信协议" class="cluster default flowchart-label"><rect height="106" width="775.109375" y="8" x="8" ry="0" rx="0" style=""/><g transform="translate(363.5546875, 8)" class="cluster-label"><foreignObject height="22" width="64"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="nodeLabel">通信协议</span></div></foreignObject></g></g></g><g class="edgePaths"/><g class="edgeLabels"/><g class="nodes"><g transform="translate(142.2421875, 61)" id="flowchart-H-1890" class="node default default flowchart-label"><rect height="53" width="198.484375" y="-26.5" x="-99.2421875" ry="0" rx="0" style="" class="basic label-container"/><g transform="translate(-91.7421875, -19)" style="" class="label"><rect/><foreignObject height="38" width="183.484375"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="nodeLabel">HTTP REST API<br />mcp-client → mcp-router</span></div></foreignObject></g></g><g transform="translate(390.7265625, 61)" id="flowchart-I-1891" class="node default default flowchart-label"><rect height="53" width="198.484375" y="-26.5" x="-99.2421875" ry="0" rx="0" style="" class="basic label-container"/><g transform="translate(-91.7421875, -19)" style="" class="label"><rect/><foreignObject height="38" width="183.484375"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="nodeLabel">SSE + JSON-RPC 2.0<br />mcp-client ↔ mcp-router</span></div></foreignObject></g></g><g transform="translate(644.0390625, 61)" id="flowchart-J-1892" class="node default default flowchart-label"><rect height="56" width="208.140625" y="-28" x="-104.0703125" ry="0" rx="0" style="" class="basic label-container"/><g transform="translate(-96.5703125, -20.5)" style="" class="label"><rect/><foreignObject height="41" width="193.140625"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="nodeLabel">MCP协议 + SSE<br />mcp-router ↔ mcp-servers</span></div></foreignObject></g></g></g></g><g transform="translate(1581.9453125, 53)" id="flowchart-A-1868" class="node default default flowchart-label"><rect height="56" width="92.015625" y="-28" x="-46.0078125" ry="0" rx="0" style="" class="basic label-container"/><g transform="translate(-38.5078125, -20.5)" style="" class="label"><rect/><foreignObject height="41" width="77.015625"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="nodeLabel">mcp-client<br />端口:8071</span></div></foreignObject></g></g><g transform="translate(1555.24609375, 185.5)" id="flowchart-B-1869" class="node default default flowchart-label"><rect height="56" width="95.828125" y="-28" x="-47.9140625" ry="0" rx="0" style="" class="basic label-container"/><g transform="translate(-40.4140625, -20.5)" style="" class="label"><rect/><foreignObject height="41" width="80.828125"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="nodeLabel">mcp-router<br />端口:8051</span></div></foreignObject></g></g><g transform="translate(1493.7890625, 293)" id="flowchart-C-1871" class="node default default flowchart-label"><rect height="56" width="117.296875" y="-28" x="-58.6484375" ry="0" rx="0" style="" class="basic label-container"/><g transform="translate(-51.1484375, -20.5)" style="" class="label"><rect/><foreignObject height="41" width="102.296875"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="nodeLabel">mcp-server-v2<br />端口:8062</span></div></foreignObject></g></g><g transform="translate(1326.4921875, 293)" id="flowchart-D-1873" class="node default default flowchart-label"><rect height="56" width="117.296875" y="-28" x="-58.6484375" ry="0" rx="0" style="" class="basic label-container"/><g transform="translate(-51.1484375, -20.5)" style="" class="label"><rect/><foreignObject height="41" width="102.296875"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="nodeLabel">mcp-server-v1<br />端口:8061</span></div></foreignObject></g></g><g transform="translate(1159.1953125, 293)" id="flowchart-E-1875" class="node default default flowchart-label"><rect height="56" width="117.296875" y="-28" x="-58.6484375" ry="0" rx="0" style="" class="basic label-container"/><g transform="translate(-51.1484375, -20.5)" style="" class="label"><rect/><foreignObject height="41" width="102.296875"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="nodeLabel">mcp-server-v3<br />端口:8063</span></div></foreignObject></g></g><g transform="translate(1425.6328125, 53)" id="flowchart-F-1876" class="node default default flowchart-label"><rect height="59" width="120.609375" y="-29.5" x="-60.3046875" ry="0" rx="0" style="" class="basic label-container"/><g transform="translate(-52.8046875, -22)" style="" class="label"><rect/><foreignObject height="44" width="105.609375"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="nodeLabel">Nacos注册中心<br />端口:8848</span></div></foreignObject></g></g><g transform="translate(1189.40234375, 185.5)" id="flowchart-G-1884" class="node default default flowchart-label"><rect height="59" width="108.859375" y="-29.5" x="-54.4296875" ry="0" rx="0" style="" class="basic label-container"/><g transform="translate(-46.9296875, -22)" style="" class="label"><rect/><foreignObject height="44" width="93.859375"><div style="display: inline-block; white-space: nowrap;" xmlns="http://www.w3.org/1999/xhtml"><span class="nodeLabel">MySQL数据库<br />端口:3306</span></div></foreignObject></g></g></g></g></g></svg>

# MCP系统调用链路分析报告

## 系统架构概述

该系统是一个基于MCP (Model Context Protocol) 协议的分布式微服务架构，包含三个核心组件：

- **mcp-client** (端口:8071) - 客户端服务
- **mcp-router** (端口:8051) - 路由网关服务  
- **mcp-server-v2** (端口:8062) - 业务服务器

## 详细调用链路分析

### 1. 服务注册与发现机制

#### 1.1 Nacos服务注册中心 (端口:8848)
- 所有服务启动时向Nacos注册中心注册
- 服务名称规范：`{应用名}-mcp-service`
- 注册信息包含：IP地址、端口、元数据、健康状态

#### 1.2 服务注册流程
```
mcp-server-v2启动 → 注册到Nacos → 服务名: mcp-server-v2-mcp-service
mcp-router启动 → 注册到Nacos → 发现所有mcp-server服务
mcp-client启动 → 配置mcp-router地址 → 建立连接
```

### 2. 通信协议栈

#### 2.1 mcp-client → mcp-router
- **协议**: HTTP REST API + SSE (Server-Sent Events)
- **端口**: 8071 → 8051
- **数据格式**: JSON
- **主要接口**:
  - `GET /mcp-client/api/v1/tools/list` - 获取工具列表
  - `POST /mcp-client/api/v1/tools/call` - 调用工具
  - `GET /mcp/jsonrpc/sse` - 建立SSE连接

#### 2.2 mcp-router → mcp-server-v2
- **协议**: MCP Protocol + SSE双向通信
- **端口**: 8051 → 8062
- **数据格式**: JSON-RPC 2.0
- **通信方式**: 异步响应式编程

### 3. 关键调用链路详解

#### 3.1 工具列表查询链路
```
1. mcp-client发起请求
   GET /mcp-client/api/v1/tools/list

2. McpClientController.listTools()
   ↓
3. CustomMcpClient.listTools()
   ↓
4. McpRouterService通过SSE连接发送JSON-RPC请求
   {"jsonrpc":"2.0","method":"tools/list","id":"xxx"}
   ↓
5. mcp-router接收SSE消息
   McpSseController.handleMcpMessage()
   ↓
6. McpJsonRpcController.processRequest()
   ↓
7. McpServerServiceImpl.getAllTools()
   ↓
8. 通过McpAsyncClient调用各个mcp-server
   ↓
9. mcp-server-v2返回工具列表
   PersonManagementTool: getAllPersons, addPerson, deletePerson
   ↓
10. 响应通过SSE返回给mcp-client
```

#### 3.2 工具调用链路
```
1. mcp-client发起工具调用
   POST /mcp-client/api/v1/tools/call
   Body: {"toolName":"getAllPersons","arguments":{}}

2. McpClientController.callTool()
   ↓
3. CustomMcpClient.callTool()
   ↓
4. McpRouterService发送JSON-RPC请求
   {"jsonrpc":"2.0","method":"tools/call","params":{"name":"getAllPersons","arguments":{}},"id":"xxx"}
   ↓
5. mcp-router路由到对应服务器
   McpServerServiceImpl.callToolOnServer()
   ↓
6. 建立与mcp-server-v2的SSE连接
   GET /sse?sessionId=xxx
   ↓
7. 发送MCP工具调用请求
   McpAsyncClient.callTool()
   ↓
8. mcp-server-v2执行业务逻辑
   PersonManagementTool.getAllPersons()
   ↓
9. 查询MySQL数据库
   PersonRepository.findAll()
   ↓
10. 返回结果通过SSE链路返回
```

### 4. 数据流转格式

#### 4.1 JSON-RPC 2.0请求格式
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "getAllPersons",
    "arguments": {}
  },
  "id": "request-1234"
}
```

#### 4.2 JSON-RPC 2.0响应格式
```json
{
  "jsonrpc": "2.0",
  "result": {
    "content": [
      {"id": 1, "firstName": "John", "lastName": "Doe", "age": 30}
    ],
    "isError": false
  },
  "id": "request-1234"
}
```

### 5. 关键技术特性

#### 5.1 响应式编程
- 使用Spring WebFlux和Reactor
- 非阻塞I/O处理
- 背压控制和流量管理

#### 5.2 连接管理
- SSE长连接维护
- 连接池管理
- 自动重连机制
- 会话生命周期管理

#### 5.3 错误处理
- 统一异常处理
- 超时控制
- 重试机制
- 优雅降级

### 6. 性能优化机制

#### 6.1 缓存策略
- 工具列表缓存 (TTL: 300秒)
- 服务发现结果缓存
- 连接复用

#### 6.2 负载均衡
- Nacos服务发现
- 健康检查
- 故障转移

### 7. 监控与日志

#### 7.1 日志级别配置
```yaml
logging:
  level:
    com.nacos.mcp: DEBUG
    org.springframework.ai: DEBUG
    root: INFO
```

#### 7.2 健康检查端点
- `/actuator/health` - 服务健康状态
- `/actuator/info` - 服务信息

### 8. 安全机制

#### 8.1 CORS配置
```yaml
cors:
  allowed-origins: "*"
  allowed-methods: GET,POST,PUT,DELETE,OPTIONS
  allowed-headers: "*"
```

#### 8.2 认证授权
- 可选的authToken验证
- 客户端ID识别
- 会话管理

## 总结

该系统采用现代微服务架构设计，通过MCP协议实现标准化的工具调用接口，使用SSE实现实时双向通信，通过Nacos实现服务发现和配置管理。整个调用链路具有高可用性、可扩展性和良好的性能特征。