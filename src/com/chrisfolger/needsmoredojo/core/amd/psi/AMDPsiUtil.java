package com.chrisfolger.needsmoredojo.core.amd.psi;

import com.chrisfolger.needsmoredojo.core.amd.AMDImport;
import com.chrisfolger.needsmoredojo.core.amd.define.DefineResolver;
import com.chrisfolger.needsmoredojo.core.amd.define.DefineStatement;
import com.chrisfolger.needsmoredojo.core.amd.importing.UnusedImportsRemover;
import com.chrisfolger.needsmoredojo.core.amd.objectmodel.DeclareResolver;
import com.chrisfolger.needsmoredojo.core.amd.objectmodel.DeclareStatementItems;
import com.intellij.lang.javascript.psi.*;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AMDPsiUtil
{
    public enum Direction
    {
        UP,
        DOWN,
        NONE
    }

    public static PsiElement getDefineForVariable(PsiFile file, String textToCompare)
    {
        List<PsiElement> defines = new ArrayList<PsiElement>();
        List<PsiElement> parameters = new ArrayList<PsiElement>();
        new DefineResolver().gatherDefineAndParameters(file, defines, parameters);

        for(int i=0;i<parameters.size();i++)
        {
            if(i > defines.size() - 1)
            {
                return null; // amd import is being modified
            }

            if(parameters.get(i).getText().equals(textToCompare))
            {
                return defines.get(i);
            }
        }

        return null;
    }

    public static PsiElement getNearestComma(PsiElement start)
    {
        PsiElement sibling = start.getPrevSibling();
        while(sibling != null && !(sibling instanceof JSLiteralExpression) && !(sibling instanceof JSParameter))
        {
            if(sibling.getText().equals(","))
            {
                return sibling;
            }

            sibling = sibling.getPrevSibling();
        }

        return null;
    }

    /**
     * gets the next comma after an element, but stops if a literal or other element is encountered
     *
     * @param start
     * @return
     */
    public static PsiElement getNextComma(PsiElement start)
    {
        PsiElement sibling = start.getNextSibling();
        while(sibling != null && !(sibling instanceof JSLiteralExpression) && !(sibling instanceof JSParameter))
        {
            if(sibling.getText().equals(","))
            {
                return sibling;
            }

            sibling = sibling.getNextSibling();
        }

        return null;
    }

    /**
     * gets the next comma after an element OR in the case of the last define in the list it will get whitespace
     * or the bracket.
     *
     * @param start
     * @return
     */
    public static PsiElement getNextDefineTerminator(PsiElement start)
    {
        PsiElement sibling = start.getNextSibling();
        while(sibling != null && !(sibling instanceof JSLiteralExpression) && !(sibling instanceof JSParameter))
        {
            if(sibling instanceof PsiWhiteSpace)
            {
                return sibling;
            }

            sibling = sibling.getNextSibling();
        }

        return start.getParent().getLastChild();
    }

    /**
     * gets a comment after the define literal, if it has one.
     *
     * Does not stop at commas
     * @param start
     * @return
     */
    @Nullable
    public static PsiElement getNonIgnoreCommentAfterLiteral(PsiElement start)
    {
        Set<String> terminators = new HashSet<String>();
        Set<String> exclusions = new HashSet<String>();
        exclusions.add(UnusedImportsRemover.IGNORE_COMMENT);

        PsiElement comment = getNextElementOfType(start, PsiComment.class, terminators, exclusions);
        if(comment != null)
        {
            return comment;
        }

        return null;
    }

    /**
     * gets an ignore comment after the define literal but before the comma, if it has one.
     * @param start
     * @return
     */
    @Nullable
    public static PsiElement getIgnoreCommentAfterLiteral(PsiElement start)
    {
        Set<String> terminators = new HashSet<String>();
        terminators.add(",");
        PsiElement ignoreComment = getNextElementOfType(start, PsiComment.class, terminators, new HashSet<String>());
        if(ignoreComment != null && ignoreComment.getText().equals(UnusedImportsRemover.IGNORE_COMMENT))
        {
            return ignoreComment;
        }

        return null;
    }

    public static PsiElement getNextElementOfType(PsiElement start, Class type, Set<String> terminators, Set<String> exclusions)
    {
        PsiElement sibling = start.getNextSibling();
        while(sibling != null && !(sibling instanceof JSLiteralExpression) && !(sibling instanceof JSParameter) && !(sibling.getText().equals("]")) && !terminators.contains(sibling.getText()))
        {
            if(type.isInstance(sibling) && !exclusions.contains(sibling.getText()))
            {
                return sibling;
            }

            sibling = sibling.getNextSibling();
        }

        return null;
    }

    public static JSLiteralExpression getNearestLiteralExpression(PsiElement element, Direction direction)
    {
        PsiElement node = element;
        if(direction == Direction.UP)
        {
            node = element.getPrevSibling();
        }
        else
        {
            node = element.getNextSibling();
        }

        int tries = 0;
        while(tries < 5)
        {
            if(node instanceof  JSLiteralExpression)
            {
                return (JSLiteralExpression) node;
            }

            if(node == null)
            {
                return null;
            }

            if(direction == Direction.UP)
            {
                node = node.getPrevSibling();
            }
            else
            {
                node = node.getNextSibling();
            }

            tries ++;
        }

        return null;
    }

    public static void removeTrailingCommas(Set<PsiElement> deleteList, JSArrayLiteralExpression literal, PsiElement function)
    {
        try
        {
            PsiElement trailingComma = AMDPsiUtil.getNearestComma(literal.getLastChild());
            if(trailingComma != null)
            {
                deleteList.add(trailingComma);
                trailingComma.delete();
            }
        }
        catch(Exception e)
        {
            System.out.println(e);
        }

        /*
        at first this block was not here and for some reason trailing commas in the function argument list
        were still deleted. I'm not sure why, but I decided to make it explicit.
         */
        try
        {
            PsiElement trailingComma = AMDPsiUtil.getNearestComma(function.getLastChild());
            if(trailingComma != null)
            {
                deleteList.add(trailingComma);
                trailingComma.delete();
            }
        }
        catch(Exception e)
        {
            System.out.println(e);
        }
    }

    /**
     * Removes a define literal from the array
     *
     * @param element the element to remove
     * @param deleteList a list that contains a list of elements to delete. This is done in case we want to run the
     *                   actual deletion later.
     */
    public static void removeDefineLiteral(PsiElement element, Set<PsiElement> deleteList)
    {
        deleteList.add(element);

        // special case for when the element we're removing is last on the list
        PsiElement sibling = element.getNextSibling();
        if(sibling != null && (sibling instanceof PsiWhiteSpace || sibling.getText().equals("]")))
        {
            deleteList.add(AMDPsiUtil.getNearestComma(sibling));
        }

        // only remove the next sibling if it's a comma
        PsiElement comma = getNextComma(element);
        if(comma != null)
        {
            deleteList.add(comma);
        }

        PsiElement nextSibling = element.getNextSibling();
        if(nextSibling != null && !nextSibling.getText().equals("]"))
        {
            deleteList.add(element.getNextSibling());
        }
    }

    public static void removeParameter(PsiElement element, Set<PsiElement> deleteList)
    {
        deleteList.add(element);

        PsiElement nextSibling = element.getNextSibling();

        // only remove commas at the end
        if(nextSibling != null && nextSibling.getText().equals(","))
        {
            deleteList.add(element.getNextSibling());
        }
    }

    public static void removeSingleImport(@NotNull AMDImport amdImport)
    {
        JSArrayLiteralExpression literal = (JSArrayLiteralExpression) amdImport.getLiteral().getParent();
        PsiElement function = amdImport.getParameter().getParent();

        Set<PsiElement> elementsToDelete = new LinkedHashSet<PsiElement>();

        // if there is an /*NMD:Ignore*/ comment, delete it as well.
        PsiElement ignoreComment = getIgnoreCommentAfterLiteral(amdImport.getLiteral());
        if(ignoreComment != null)
        {
            elementsToDelete.add(ignoreComment);
        }

        removeParameter(amdImport.getParameter(), elementsToDelete);
        AMDPsiUtil.removeDefineLiteral(amdImport.getLiteral(), elementsToDelete);

        for(PsiElement element : elementsToDelete)
        {
            try
            {
                element.delete();
            }
            catch(Exception e)
            {
                // something happened, but it's probably not important when deleting.
            }
        }

        AMDPsiUtil.removeTrailingCommas(elementsToDelete, literal, function);
    }

    /**
     * Given an element that is really a reference to an imported module, find the nearest define or require import
     * that matches.
     *
     * @param psiElement
     * @return an element representing the define's string literal (like 'dojo/dom-construct')
     */
    public static PsiElement resolveReferencedDefine(PsiElement psiElement)
    {
        boolean isReference = psiElement instanceof JSReferenceExpression || (psiElement.getParent() != null && psiElement.getParent() instanceof JSReferenceExpression);
        boolean isNew = psiElement instanceof JSNewExpression || (psiElement.getParent() != null && psiElement.getParent() instanceof JSNewExpression);
        boolean isParameter = psiElement instanceof JSParameter || (psiElement.getParent() != null && psiElement.getParent() instanceof JSParameter);

        // support for reference or new expression
        if(!(isReference || isNew || isParameter))
        {
            return null;
        }

        DefineResolver resolver = new DefineResolver();
        DefineStatement defineStatement = resolver.getNearestImportBlock(psiElement);

        if(defineStatement == null)
        {
            return null;
        }

        for (int x = 0; x < defineStatement.getFunction().getParameters().length; x++)
        {
            JSParameter parameter = defineStatement.getFunction().getParameters()[x];
            JSExpression define = defineStatement.getArguments().getExpressions()[x];

            if(parameter.getText().equals(psiElement.getText()))
            {
                return define;
            }
        }

        return null;
    }

    /**
     * Determines if a file has the method in question
     * @param file
     * @param methodName
     * @param useApproximatingVisitor some dojo modules are not straight-forward. So for this case, pass true for this
     *                                and it will search a file for a property or a reference to the method name in question
     * @return
     */
    public static @Nullable PsiElement fileHasMethod(PsiFile file, String methodName, boolean useApproximatingVisitor)
    {
        DeclareStatementItems declareObject = new DeclareResolver().getDeclareObject(file);

        if((declareObject == null || declareObject.getMethodsToConvert() == null) && !useApproximatingVisitor)
        {
            return null;
        }

        if(declareObject != null && declareObject.getMethodsToConvert() != null)
        {
            for(JSProperty property : declareObject.getMethodsToConvert())
            {
                if(property.getName().equals(methodName))
                {
                    return property;
                }
            }
        }

        if(useApproximatingVisitor)
        {
            JSMethodLookupVisitor visitor = new JSMethodLookupVisitor(methodName);
            file.acceptChildren(visitor);
            return visitor.getFoundElement();
        }

        return null;
    }
}
