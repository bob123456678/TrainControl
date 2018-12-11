/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package layout;

/**
 *
 * @author Adam
 */
public class Segments
{
/*"""
    Graph points and segments
"""

inter = ['T2', 'T3', 'T4', 'T5', 'T6', 'B1', 'B3', 'B4', 'B6']
stations = [('B5','20'),('T1','33'),('1R','21'), ('2R','23'), ('3R','16'),
    ('4R','17'), ('11L','36'), ('12L','37'), ('13L','41'), ('14L','42')]
edges = [
         ('B6','1R'), ('B6', '2R'), ('B5', '3R'), ('B5', '4R'),
         ('1R', 'B1'), ('2R', 'B1'), ('B1', 'B3'), ('B1', 'B4'),
         ('3R', 'B4'),('4R','B4'),('B3','T2'),('B4','T1'),
         ('T1', '11L'),('T1','12L'),('T2','13L'),('T2','14L'),
         ('11L','T4'),('12L','T4'),('13L','T6'),('14L','T3'),
         ('T6','B6'),('T5','B5'), ('T4', 'T5'), ('T4', 'T6'),
         ('T3', 'T6')
]

def _B6_1R(c):
    c.getSwitch('1').turnout()
    c.getSignal('k13').red()
    c.getSignal('k11').green()

def _B6_2R(c):
    c.getSwitch('83').straight()
    c.getSwitch('1').straight()
    c.getSignal('k12').green()
    c.getSignal('k70').red()
    
def _1R_B1(c):
    c.getSwitch('5-6').right()
    c.getSignal('k14').green()
    c.getSignal('k13').green()
    
def _B1_B3(c):
    c.getSwitch('17').straight()
    
def _B1_B4(c):
    c.getSwitch('17').turnout()
    c.getSignal('k14').green()
    
def _3R_B4(c):
    c.getSignal('k15').green()
    
def _4R_B4(c):
    c.getSwitch('19-20').left()
    c.getSignal('k16').green()
    
def _T2_13L(c):
    c.getSwitch('40').straight()
    c.getSignal('k75').green()
    c.getSignal('k43').red()
    
def _T2_14L(c):
    c.getSwitch('40').turnout()
    c.getSignal('k76').green()
    c.getSignal('k44').red()
    
def _13L_T6(c):
    c.getSignal('k46').green()
    c.getSignal('k43').green()
    
def _14L_T3(c):
    c.getSignal('k44').green()
    
def _B5_3R(c):
    c.getSwitch('2').turnout()
    c.getSignal('k71').green()
    c.getSignal('k15').red()
    c.getSignal('k9').green()

def _B5_4R(c):
    c.getSwitch('2').straight()
    c.getSignal('k72').green()
    c.getSignal('k16').red()
    c.getSignal('k9').green()

def _2R_B1(c):
    c.getSignal('k14').green()
    c.getSignal('k70').green()
    
def _T1_11L(c):
    c.getSignal('k45').green()
    c.getSwitch('39').straight()
    c.getSignal('k73').green()
    c.getSignal('k41').red()

def _T1_12L(c):
    c.getSignal('k45').green()
    c.getSwitch('39').turnout()
    c.getSignal('k74').green()
    c.getSignal('k42').red()
        
def _11L_T4(c):
    c.getSignal('k41').green()
    
def _12L_T4(c):
    c.getSignal('k42').green()
    
def _T4_T5(c):
    c.getSwitch('35').straight()
    
def _T4_T6(c):
    c.getSwitch('35').turnout()
    
def _T3_T6(c):
    c.getSignal('k46').green()
    
def _T5_B5(c):
    c.getSignal('k9').red()
    
def _B4_T1(c):
    c.getSignal('k45').red() */
    
}
