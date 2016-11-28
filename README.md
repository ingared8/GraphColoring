
This scala code covers two versions of Graph Vertex coloring which can be used in saprk GraphX/ GraphFrames.
The code and examples are self explanantory.

/* I'm just releasing part of the code which could be useful for community from a private project and sorry for not adding sbt build files */

#
# Part I - Graph Vertex Coloring in Spark GraphX / GraphFrames
In graph theory, graph coloring or vertex/node coloring or k-coloring is an assignment of colors to graph vertices/nodes such that no two vertices which are connected should share the same color.

One can define graph coloring as

 Given an undirected graph G = (V, E), a k-color coloring assigns a color
   - color(v) ∈ {1, 2, ..., k} to each node v ∈ V
   - such that for ∀(u, v) ∈ E,  color(u) != color(v)

Bounds on chromatic number  χ(G) = minG(k)

  - 1-coloring is possible if and only if G is edgeless
  - 2-coloring is possible if and only if G is a bipartite graph.
 
 In general, a proven greedy coloring shows that every graph can be colored with one more color than the maximum vertex degree ∆(G)

    χ(G) <= maximum vertex degree + 1
    χ(G) <=  ∆(G) + 1

Graph Vertex Coloring is also popularly known as K-MinColoring where K represents the minimum number of colors possible to color the graph such that no two neighbors has the same color.

In this algorithm, we try to solve graph coloring by considering
    
        k =  ∆(G) + 1

   ∆(G) is represented as ∆ for simplicity.
 *******************************************
 A simple naive algorithm  for ColorReduction
 *******************************************

    function ColorReduction(G = (V, E), ∆)
        n = |V |
        for v = Range(n) do
            color(v) = v
        end for
        
        for v = ∆ + 2, n do
            color(v) = min({1, ..., ∆ + 1} \ {cu|(u, v) ∈ E})
        end for
     end function


    /*    
        color(v) = min({1, ..., ∆+1 } \ {cu|(u, v) ∈ E}) 
        indicates that a node should choose a min value from  
        (1,...,∆ + 1) which is not taken by its neighbors.
        
        For example, if my neighbors are colored (1,2,4,5) , 
        then the node will choose its color as 3.
    */
    
More info @  https://en.wikipedia.org/wiki/Graph_coloring

