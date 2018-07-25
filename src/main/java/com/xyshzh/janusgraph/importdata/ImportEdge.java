package com.xyshzh.janusgraph.importdata;

import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;

/**
 * 
 * {"outVertexId":1,"inVertexId":2,"label":"OWN","key1":"value1","key2":"value2","key3":"value3"}
 * 其中label,outVertexId,inVertexId为必须项,其他内容可自由添加(Schema中存在的)
 * A ---> B,则outVertexId=A,inVertexId=B
 * 
 * @author Shengjun Liu
 * @version 2018-01-16
 *
 */
public class ImportEdge {

  public static void main(String[] args) {

    // 简单的将参数生成Map集合.
    java.util.HashMap<String, Object> options = com.xyshzh.janusgraph.utils.ArgsUtils.initOptions(args);

    // 试图打开文件,文件使用结束后或出现异常后,在finally内关闭文件
    com.xyshzh.janusgraph.datasource.Read reader = new com.xyshzh.janusgraph.datasource.ReadFile(
        options.get("file").toString());

    if (!reader.check()) { // 检测数据源
      System.out.println("文件异常,请重试.");
      System.exit(0);
    }

    java.util.concurrent.atomic.AtomicInteger total = new java.util.concurrent.atomic.AtomicInteger(0); // 计数器

    final int thread = Integer.valueOf(options.getOrDefault("thread", "1").toString()); // 并发度

    java.util.concurrent.CountDownLatch countDownLatch = new java.util.concurrent.CountDownLatch(thread); // 闭锁

    boolean checkedge = options.containsKey("checkedge"); // 检查关系信息是否存在,初次导入可以忽略

    boolean setvertexid = options.containsKey("setvertexid"); // 自定义id

    String[] keys = options.containsKey("keys") ? options.get("keys").toString().split(",") : new String[] {}; // 如果不自定义id,则通过这些字段判断关系信息是否存在

    String[] fkeys = options.containsKey("fkeys") ? options.get("fkeys").toString().split(",") : new String[] {}; // 如果不自定义id,则通过这些字段判断开始节点信息是否存在

    String[] tkeys = options.containsKey("tkeys") ? options.get("tkeys").toString().split(",") : new String[] {}; // 如果不自定义id,则通过这些字段判断结束节点信息是否存在

    try {
      com.xyshzh.janusgraph.core.GraphFactory graphFactory = new com.xyshzh.janusgraph.core.GraphFactory(); // 创建图数据库连接
      for (int i = 0; i < thread; i++) {
        new Thread(new Runnable() {
          @Override
          public void run() {
            org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource g = graphFactory.getG(); // 获取遍历源,判断是否存在使用
            String tempString = null;
            try {
              while ((tempString = reader.readLine()) != null) {
                total.addAndGet(1); // 每读一行,计数器加一
                net.sf.json.JSONObject content = net.sf.json.JSONObject.fromObject(tempString);

                String label = content.containsKey("~label") ? content.getString("~label") : null; // 获取label信息
                if (null == label || "".equals(label.trim())) { // 如果label内容为空,则忽略本条记录
                  System.out.println("Current Position >> " + total.get() + " >> label :: " + label + " >> ignore.");
                  continue;
                }

                Long outVertexId = content.containsKey("~outVertexId") ? content.getLong("~outVertexId") : null; // 获取outVertexId信息
                if (setvertexid && (null == outVertexId || 1 > outVertexId)) { // 如果outVertexId内容为空或小于1,则忽略本条记录
                  System.out.println(
                      "Current Position >> " + total.get() + " >> outVertexId :: " + outVertexId + " >> ignore.");
                  continue;
                }

                Long inVertexId = content.containsKey("~inVertexId") ? content.getLong("~inVertexId") : null; // 获取inVertexId信息
                if (setvertexid && (null == inVertexId || 1 > inVertexId)) { // 如果inVertexId内容为空或小于1,则忽略本条记录
                  System.out.println(
                      "Current Position >> " + total.get() + " >> inVertexId :: " + inVertexId + " >> ignore.");
                  continue;
                }

                // 判断关系是否存在
                if (checkedge) {
                  java.util.HashMap<String, Object> kvs = new java.util.HashMap<String, Object>(); // 临时判定条件
                  for (String key : keys) { // 判断字段集合
                    if (content.containsKey(key)) { // 判断字段是否存在,存在则参与计算
                      kvs.put(key.toString(), content.get(key));
                    }
                  }
                  org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal<Edge, Edge> e = g.E();
                  for (java.util.Map.Entry<String, Object> kv : kvs.entrySet()) {
                    e = e.has(kv.getKey(), kv.getValue());
                  }
                  if (e.limit(1).hasNext()) {
                    continue;
                  }
                }

                // 获取起始点和结束点
                Vertex startV = null;
                Vertex endV = null;
                if (setvertexid) {
                  org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal<Vertex, Vertex> starts = g
                      .V(outVertexId);
                  org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal<Vertex, Vertex> endVs = g
                      .V(inVertexId);
                  if (starts.hasNext()) {
                    startV = starts.next();
                  }
                  if (endVs.hasNext()) {
                    endV = endVs.next();
                  }
                } else {
                  org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal<Vertex, Vertex> starts = g
                      .V();
                  for (String key : fkeys) { // 判断字段集合
                    if (content.containsKey(key)) { // 判断字段是否存在,存在则参与计算
                      starts = starts.has(key.toString(), content.get(key));
                    }
                  }
                  org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal<Vertex, Vertex> endVs = g.V();
                  for (String key : tkeys) { // 判断字段集合
                    if (content.containsKey(key)) { // 判断字段是否存在,存在则参与计算
                      endVs.has(key.toString(), content.get(key));
                    }
                  }
                  if (starts.hasNext()) {
                    startV = starts.next();
                  }
                  if (endVs.hasNext()) {
                    endV = endVs.next();
                  }
                }

                // 起始点和结束点不为null时添加关系
                if (null != startV && null != endV) {
                  Edge e = startV.addEdge(label, endV);
                  // 将数据中的其他字段添加到Edge
                  for (Object key : content.keySet()) {
                    if (key.equals("~inVertexId") || key.equals("~outVertexId") || key.equals("~label") || key.equals("~typeId") || key.equals("~relationId")) { // 忽略inVertexId & outVertexId & label
                      continue;
                    }
                    e.property(key.toString(), content.get(key));
                  }
                } else {
                  System.out.println("Current Position >> " + "起始点::" + startV + " >> 结束点::" + endV + " >> 关系::" + label + tempString);
                }
              }
              g.tx().commit();
              g.tx().open();
            } catch (Exception e) {
              e.printStackTrace();
              System.out.println("Current Position >> " + total.get() + " :: 线程出现异常 :: " + tempString);
            }
            countDownLatch.countDown();
          }
        }).start();
      }

      try {
        countDownLatch.await();
      } catch (InterruptedException e) {
        e.printStackTrace();
      } finally {
        graphFactory.close();
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      reader.close(); // 执行最后,是否异常,皆关闭文件
    }
    System.out.println("I'm File Thread, I'm Over!");
  }

}