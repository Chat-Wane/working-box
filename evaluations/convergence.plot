set term postscript eps color blacktext "Helvetica" 24

set output "convergence.eps"
plot "meow.dat" u 0:1, "meow.dat" u 0:2