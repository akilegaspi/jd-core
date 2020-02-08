/*
 * Copyright (c) 2008, 2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.core.v1.service.converter.classfiletojavasyntax.visitor;

import org.jd.core.v1.model.javasyntax.AbstractJavaSyntaxVisitor;
import org.jd.core.v1.model.javasyntax.declaration.*;
import org.jd.core.v1.model.javasyntax.expression.*;
import org.jd.core.v1.model.javasyntax.reference.InnerObjectReference;
import org.jd.core.v1.model.javasyntax.reference.ObjectReference;
import org.jd.core.v1.model.javasyntax.statement.*;
import org.jd.core.v1.model.javasyntax.type.*;
import org.jd.core.v1.service.converter.classfiletojavasyntax.model.javasyntax.declaration.ClassFileBodyDeclaration;
import org.jd.core.v1.service.converter.classfiletojavasyntax.model.javasyntax.declaration.ClassFileConstructorDeclaration;
import org.jd.core.v1.service.converter.classfiletojavasyntax.model.javasyntax.declaration.ClassFileMethodDeclaration;
import org.jd.core.v1.service.converter.classfiletojavasyntax.model.javasyntax.declaration.ClassFileStaticInitializerDeclaration;
import org.jd.core.v1.service.converter.classfiletojavasyntax.model.javasyntax.expression.*;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.TypeMaker;
import org.jd.core.v1.util.DefaultList;

import java.util.Map;

import static org.jd.core.v1.model.javasyntax.declaration.Declaration.FLAG_BRIDGE;
import static org.jd.core.v1.model.javasyntax.declaration.Declaration.FLAG_SYNTHETIC;
import static org.jd.core.v1.model.javasyntax.type.ObjectType.TYPE_OBJECT;

public class AddCastExpressionVisitor extends AbstractJavaSyntaxVisitor {
    protected SearchFirstLineNumberVisitor searchFirstLineNumberVisitor = new SearchFirstLineNumberVisitor();

    protected TypeMaker typeMaker;
    protected Map<String, BaseType> typeBounds;
    protected Type returnedType;
    protected BaseType exceptionTypes;
    protected Type type;

    public AddCastExpressionVisitor(TypeMaker typeMaker) {
        this.typeMaker = typeMaker;
    }

    @Override
    public void visit(BodyDeclaration declaration) {
        BaseMemberDeclaration memberDeclarations = declaration.getMemberDeclarations();

        if (memberDeclarations != null) {
            Map<String, BaseType> tb = typeBounds;

            typeBounds = ((ClassFileBodyDeclaration)declaration).getTypeBounds();
            memberDeclarations.accept(this);
            typeBounds = tb;
        }
    }

    @Override
    public void visit(FieldDeclaration declaration) {
        if ((declaration.getFlags() & FLAG_SYNTHETIC) == 0) {
            Type t = type;

            type = declaration.getType();
            declaration.getFieldDeclarators().accept(this);
            type = t;
        }
    }

    @Override
    public void visit(FieldDeclarator declarator) {
        VariableInitializer variableInitializer = declarator.getVariableInitializer();

        if (variableInitializer != null) {
            int extraDimension = declarator.getDimension();

            if (extraDimension == 0) {
                variableInitializer.accept(this);
            } else {
                Type t = type;

                type = type.createType(type.getDimension() + extraDimension);
                variableInitializer.accept(this);
                type = t;
            }
        }
    }


    @Override
    public void visit(StaticInitializerDeclaration declaration) {
        BaseStatement statements = declaration.getStatements();

        if (statements != null) {
            Map<String, BaseType> tb = typeBounds;

            typeBounds = ((ClassFileStaticInitializerDeclaration)declaration).getTypeBounds();
            statements.accept(this);
            typeBounds = tb;
        }
    }

    @Override
    public void visit(ConstructorDeclaration declaration) {
        if ((declaration.getFlags() & (FLAG_SYNTHETIC|FLAG_BRIDGE)) == 0) {
            BaseStatement statements = declaration.getStatements();

            if (statements != null) {
                Map<String, BaseType> tb = typeBounds;
                BaseType et = exceptionTypes;

                typeBounds = ((ClassFileConstructorDeclaration) declaration).getTypeBounds();
                exceptionTypes = declaration.getExceptionTypes();
                statements.accept(this);
                typeBounds = tb;
                exceptionTypes = et;
            }
        }
    }

    @Override
    public void visit(MethodDeclaration declaration) {
        if ((declaration.getFlags() & (FLAG_SYNTHETIC|FLAG_BRIDGE)) == 0) {
            BaseStatement statements = declaration.getStatements();

            if (statements != null) {
                Map<String, BaseType> tb = typeBounds;
                Type rt = returnedType;
                BaseType et = exceptionTypes;

                typeBounds = ((ClassFileMethodDeclaration) declaration).getTypeBounds();
                returnedType = declaration.getReturnedType();
                exceptionTypes = declaration.getExceptionTypes();
                statements.accept(this);
                typeBounds = tb;
                returnedType = rt;
                exceptionTypes = et;
            }
        }
    }

    @Override
    public void visit(LambdaIdentifiersExpression expression) {
        BaseStatement statements = expression.getStatements();

        if (statements != null) {
            Type rt = returnedType;

            returnedType = ObjectType.TYPE_OBJECT;
            statements.accept(this);
            returnedType = rt;
        }
    }

    @Override
    public void visit(ReturnExpressionStatement statement) {
        statement.setExpression(updateExpression(returnedType, statement.getExpression(), false));
    }

    @Override
    public void visit(ThrowStatement statement) {
        if ((exceptionTypes != null) && (exceptionTypes.size() == 1)) {
            Type exceptionType = exceptionTypes.getFirst();

            if (exceptionType.isGenericType() && !statement.getExpression().getType().equals(exceptionType)) {
                statement.setExpression(addCastExpression(exceptionType, statement.getExpression()));
            }
        }
    }

    @Override
    public void visit(LocalVariableDeclaration declaration) {
        Type t = type;

        type = declaration.getType();
        declaration.getLocalVariableDeclarators().accept(this);
        type = t;
    }

    @Override
    public void visit(LocalVariableDeclarator declarator) {
        VariableInitializer variableInitializer = declarator.getVariableInitializer();

        if (variableInitializer != null) {
            int extraDimension = declarator.getDimension();

            if (extraDimension == 0) {
                variableInitializer.accept(this);
            } else {
                Type t = type;

                type = type.createType(type.getDimension() + extraDimension);
                variableInitializer.accept(this);
                type = t;
            }
        }
    }

    @Override
    public void visit(ArrayVariableInitializer declaration) {
        if (type.getDimension() == 0) {
            acceptListDeclaration(declaration);
        } else {
            Type t = type;

            type = type.createType(type.getDimension() - 1);
            acceptListDeclaration(declaration);
            type = t;
        }
    }

    @Override
    public void visit(ExpressionVariableInitializer declaration) {
        Expression expression = declaration.getExpression();

        if (expression.isNewInitializedArray()) {
            NewInitializedArray nia = (NewInitializedArray)expression;
            Type t = type;

            type = nia.getType();
            nia.getArrayInitializer().accept(this);
            type = t;
        } else {
            declaration.setExpression(updateExpression(type, expression, false));
        }
    }

    @Override
    public void visit(SuperConstructorInvocationExpression expression) {
        BaseExpression parameters = expression.getParameters();

        if (parameters != null) {
            boolean force = (parameters.size() > 0) && typeMaker.multipleMethods(expression.getObjectType().getInternalName(), "<init>", parameters.size());
            expression.setParameters(updateExpressions(((ClassFileSuperConstructorInvocationExpression)expression).getParameterTypes(), parameters, force));
        }
    }

    @Override
    public void visit(ConstructorInvocationExpression expression) {
        BaseExpression parameters = expression.getParameters();

        if (parameters != null) {
            boolean force = (parameters.size() > 0) && typeMaker.multipleMethods(expression.getObjectType().getInternalName(), "<init>", parameters.size());
            expression.setParameters(updateExpressions(((ClassFileConstructorInvocationExpression)expression).getParameterTypes(), parameters, force));
        }
    }

    @Override
    public void visit(MethodInvocationExpression expression) {
        BaseExpression parameters = expression.getParameters();

        if (parameters != null) {
            boolean force = (parameters.size() > 0) && typeMaker.multipleMethods(expression.getInternalTypeName(), expression.getName(), parameters.size());
            expression.setParameters(updateExpressions(((ClassFileMethodInvocationExpression)expression).getParameterTypes(), parameters, force));
        }
        
        expression.getExpression().accept(this);
    }

    @Override
    public void visit(NewExpression expression) {
        BaseExpression parameters = expression.getParameters();

        if (parameters != null) {
            expression.setParameters(updateExpressions(((ClassFileNewExpression)expression).getParameterTypes(), parameters, false));
        }
    }

    @Override
    public void visit(NewInitializedArray expression) {
        ArrayVariableInitializer arrayInitializer = expression.getArrayInitializer();

        if (arrayInitializer != null) {
            Type t = type;

            type = expression.getType();
            arrayInitializer.accept(this);
            type = t;
        }
    }

    @Override
    public void visit(FieldReferenceExpression expression) {
        Expression exp = expression.getExpression();

        if ((exp != null) && !exp.isObjectTypeReferenceExpression()) {
            Type type = typeMaker.makeFromInternalTypeName(expression.getInternalTypeName());

            if (type.getName() != null) {
                expression.setExpression(updateExpression(type, exp, false));
            }
        }
    }

    @Override
    public void visit(BinaryOperatorExpression expression) {
        expression.getLeftExpression().accept(this);

        Expression rightExpression = expression.getRightExpression();

        if (expression.getOperator().equals("=")) {
            if (rightExpression.isMethodInvocationExpression()) {
                ClassFileMethodInvocationExpression mie = (ClassFileMethodInvocationExpression)rightExpression;

                if (mie.getTypeParameters() != null) {
                    // Do not add cast expression if method contains type parameters
                    rightExpression.accept(this);
                    return;
                }
            }

            expression.setRightExpression(updateExpression(expression.getLeftExpression().getType(), rightExpression, false));
            return;
        }

        rightExpression.accept(this);
    }

    @Override
    public void visit(TernaryOperatorExpression expression) {
        Type expressionType = expression.getType();

        expression.getCondition().accept(this);

        expression.setExpressionTrue(updateExpression(expressionType, expression.getExpressionTrue(), false));
        expression.setExpressionFalse(updateExpression(expressionType, expression.getExpressionFalse(), false));
    }

    @SuppressWarnings("unchecked")
    protected BaseExpression updateExpressions(BaseType types, BaseExpression expressions, boolean force) {
        if (expressions != null) {
            if (expressions.isList()) {
                DefaultList<Type> t = types.getList();
                DefaultList<Expression> e = expressions.getList();

                for (int i = e.size() - 1; i >= 0; i--) {
                    e.set(i, updateExpression(t.get(i), e.get(i), force));
                }
            } else {
                expressions = updateExpression(types.getFirst(), (Expression) expressions, force);
            }
        }

        return expressions;
    }

    private Expression updateExpression(Type type, Expression expression, boolean force) {
        if (expression.isNullExpression()) {
            if (force) {
                searchFirstLineNumberVisitor.init();
                expression.accept(searchFirstLineNumberVisitor);
                expression = new CastExpression(searchFirstLineNumberVisitor.getLineNumber(), type, expression);
            }
        } else {
            Type expressionType = expression.getType();

            if (!expressionType.equals(type) && !TYPE_OBJECT.equals(type)) {
                if (type.isObjectType()) {
                    if (expressionType.isObjectType()) {
                        ObjectType objectType = (ObjectType) type;
                        ObjectType expressionObjectType = (ObjectType) expressionType;

                        if (force && !objectType.getInternalName().equals(expressionObjectType.getInternalName())) {
                            // Force disambiguation of method invocation => Uses raw type
                            if (expression.isNewExpression()) {
                                ClassFileNewExpression ne = (ClassFileNewExpression)expression;
                                ne.setObjectType(ne.getObjectType().createType(null));
                            }
                            expression = addCastExpression(objectType, expression);
                        } else if (!typeMaker.isAssignable(typeBounds, objectType, expressionObjectType)) {
                            BaseTypeArgument ta1 = objectType.getTypeArguments();
                            BaseTypeArgument ta2 = expressionObjectType.getTypeArguments();
                            Type t = type;

                            if ((ta1 != null) && (ta2 != null) && !ta1.isTypeArgumentAssignableFrom(typeBounds, ta2)) {
                                // Incompatible typeArgument arguments => Uses raw type
                                t = objectType.createType(null);
                            }
                            expression = addCastExpression(t, expression);
                        }
                    } else if (expressionType.isGenericType()) {
                        expression = addCastExpression(type, expression);
                    }
                } else if (type.isGenericType()) {
                    if (expressionType.isObjectType() || expressionType.isGenericType()) {
                        expression = addCastExpression(type, expression);
                    }
                }
            }

            if (!force && expression.isCastExpression()) {
                Type ceExpressionType = expression.getExpression().getType();

                if (type.isObjectType() && ceExpressionType.isObjectType() && typeMaker.isAssignable(typeBounds, (ObjectType)type, (ObjectType)ceExpressionType)) {
                    // Remove cast expression
                    expression = expression.getExpression();
                }
            }

            expression.accept(this);
        }

        return expression;
    }

    private Expression addCastExpression(Type type, Expression expression) {
        if (expression.isCastExpression()) {
            if (type.equals(expression.getExpression().getType())) {
                return expression.getExpression();
            } else {
                CastExpression ce = (CastExpression)expression;

                ce.setType(type);
                return ce;
            }
        } else {
            searchFirstLineNumberVisitor.init();
            expression.accept(searchFirstLineNumberVisitor);
            return new CastExpression(searchFirstLineNumberVisitor.getLineNumber(), type, expression);
        }
    }

    @Override public void visit(FloatConstantExpression expression) {}
    @Override public void visit(IntegerConstantExpression expression) {}
    @Override public void visit(ConstructorReferenceExpression expression) {}
    @Override public void visit(DoubleConstantExpression expression) {}
    @Override public void visit(EnumConstantReferenceExpression expression) {}
    @Override public void visit(LocalVariableReferenceExpression expression) {}
    @Override public void visit(LongConstantExpression expression) {}
    @Override public void visit(BreakStatement statement) {}
    @Override public void visit(ByteCodeStatement statement) {}
    @Override public void visit(ContinueStatement statement) {}
    @Override public void visit(NullExpression expression) {}
    @Override public void visit(ObjectTypeReferenceExpression expression) {}
    @Override public void visit(SuperExpression expression) {}
    @Override public void visit(ThisExpression expression) {}
    @Override public void visit(TypeReferenceDotClassExpression expression) {}
    @Override public void visit(ObjectReference reference) {}
    @Override public void visit(InnerObjectReference reference) {}
    @Override public void visit(TypeArguments type) {}
    @Override public void visit(WildcardExtendsTypeArgument type) {}
    @Override public void visit(ObjectType type) {}
    @Override public void visit(InnerObjectType type) {}
    @Override public void visit(WildcardSuperTypeArgument type) {}
    @Override public void visit(Types list) {}
    @Override public void visit(TypeParameterWithTypeBounds type) {}
}
